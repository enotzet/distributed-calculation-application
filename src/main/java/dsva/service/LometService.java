package dsva.service;

import dsva.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class LometService {
    @Autowired private LogicalClockService clock;
    @Autowired private RicartAgrawalaService raService;
    @Autowired private TopologyService topology;
    @Autowired private NetworkService network;

    private final Map<String, DependencyEdge> globalWFG = new ConcurrentHashMap<>();
    private final Map<String, String> resourceHolders = new ConcurrentHashMap<>();
    private final Map<String, Long> preliminaryTimestamps = new ConcurrentHashMap<>();
    private final Map<String, List<String>> preliminaryMap = new ConcurrentHashMap<>();

    private final ReentrantLock localLock = new ReentrantLock();

    public void executeInCS(Runnable action) {
        raService.requestCS();
        try {
            int timeout = 0;
            while (!raService.isGranted() && timeout < 100) {
                Thread.sleep(100);
                timeout++;
            }
            if (raService.isGranted()) {
                raService.setInCS(true);
                action.run();
                broadcastGlobalState();
                raService.releaseCS();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public boolean sendPreliminaryRequests(List<String> resources) {
        String myId = topology.getMyId();
        final boolean[] isSafe = {true};

        executeInCS(() -> {
            long myRequestTime = clock.tick();
            preliminaryTimestamps.put(myId, myRequestTime);
            preliminaryMap.put(myId, resources);

            clock.log("[LOMET] Building dependency graph for intents: " + resources);
            rebuildWFG();

            if (detectDeadlock()) {
                clock.log("!!! DEADLOCK PREVENTED !!! Rejecting preliminary request.");
                preliminaryMap.remove(myId);
                preliminaryTimestamps.remove(myId);
                rebuildWFG(); // Откатываем граф
                isSafe[0] = false;
            }
        });
        return isSafe[0];
    }

    private void rebuildWFG() {
        globalWFG.clear();

        Map<String, String> effectiveHolders = new HashMap<>(resourceHolders);

        preliminaryMap.forEach((nodeId, resources) -> {
            if (resources != null && !resources.isEmpty()) {
                String primaryResource = resources.get(0);
                effectiveHolders.putIfAbsent(primaryResource, nodeId);
            }
        });

        preliminaryMap.forEach((p_i, wantedResources) -> {
            for (String res : wantedResources) {
                String holder = effectiveHolders.get(res);

                if (holder != null && !holder.equals(p_i)) {
                    addWaitEdge(p_i, holder, res);
                } else if (holder == null) {
                    preliminaryMap.forEach((p_j, otherWanted) -> {
                        Long tsI = preliminaryTimestamps.get(p_i);
                        Long tsJ = preliminaryTimestamps.get(p_j);

                        if (!p_i.equals(p_j) && otherWanted.contains(res) && tsI != null && tsJ != null) {
                            if (tsI > tsJ) {
                                addWaitEdge(p_i, p_j, res);
                            }
                        }
                    });
                }
            }
        });
    }

    public String acquirePreliminaryResources() {
        String myId = topology.getMyId();
        List<String> myWanted = preliminaryMap.get(myId);
        if (myWanted == null) return "NO_PRELIMINARY_INTENTS";

        clock.log("[ACQUIRE] Starting partial acquisition for: " + myWanted);

        while (true) {
            final List<String> missingResources = new ArrayList<>();
            final boolean[] acquiredSomethingNew = {false};

            executeInCS(() -> {
                for (String res : myWanted) {
                    String currentHolder = resourceHolders.get(res);

                    if (myId.equals(currentHolder)) {
                        continue;
                    }

                    if (currentHolder != null) {
                        missingResources.add(res);
                        continue;
                    }

                    boolean someoneElseHasPriority = preliminaryMap.entrySet().stream()
                            .anyMatch(entry -> {
                                String otherId = entry.getKey();
                                Long otherTs = preliminaryTimestamps.get(otherId);
                                Long myTs = preliminaryTimestamps.get(myId);

                                // Если данных о времени нет - не уступаем
                                if (otherTs == null || myTs == null) return false;

                                return !otherId.equals(myId) &&           // Не мы
                                        entry.getValue().contains(res) && // Хочет этот же ресурс
                                        otherTs < myTs &&                 // Пришел раньше нас
                                        !otherId.equals(resourceHolders.get(res)); // И еще не владеет им
                            });

                    if (!someoneElseHasPriority) {
                        resourceHolders.put(res, myId);
                        clock.log("[LOMET] Partial acquisition success: " + res);
                        acquiredSomethingNew[0] = true;
                    } else {
                        missingResources.add(res);
                    }
                }

                if (acquiredSomethingNew[0]) {
                    rebuildWFG();
                }
            });

            if (missingResources.isEmpty()) {
                clock.log("[ACQUIRE] All resources acquired successfully.");
                return "SUCCESS";
            }

            clock.log("[POLLING] Holding some, waiting for others: " + missingResources);

            for (String res : missingResources) {
                String holderId = resourceHolders.get(res);

                if (holderId != null && !holderId.equals(myId)) {
                    boolean isAlive = network.pingNode(holderId);
                    if (!isAlive) {
                        clock.log("!!! DETECTED DEAD HOLDER: " + holderId + " on resource " + res);
                        network.reportFailure(holderId);
                        break;
                    }
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "INTERRUPTED";
            }
        }
    }


    @SuppressWarnings("unchecked")
    public void sync(Map<String, Object> incoming) {
        if (incoming == null) return;

        synchronized (globalWFG) {
            Map<String, String> holders = (Map<String, String>) incoming.get("holders");
            if (holders != null) {
                resourceHolders.clear();
                resourceHolders.putAll(holders);
            }

            Map<String, List<String>> prelim = (Map<String, List<String>>) incoming.get("preliminary");
            if (prelim != null) {
                preliminaryMap.clear();
                preliminaryMap.putAll(prelim);
            }

            Map<String, Object> ts = (Map<String, Object>) incoming.get("timestamps");
            if (ts != null) {
                preliminaryTimestamps.clear();
                ts.forEach((k, v) -> {
                    if (v != null) {
                        preliminaryTimestamps.put(k, ((Number) v).longValue());
                    }
                });
            }

            rebuildWFG();
        }
    }

    public void releaseResource(String resId) {
        String myId = topology.getMyId();
        executeInCS(() -> {
            if (myId.equals(resourceHolders.get(resId))) {
                resourceHolders.remove(resId);

                globalWFG.entrySet().removeIf(e -> e.getValue().getToId().equals(myId));


                List<String> intents = preliminaryMap.get(myId);
                if (intents != null) {
                    intents.remove(resId); // Удаляем конкретный ресурс

                    if (intents.isEmpty()) {
                        preliminaryMap.remove(myId);
                        preliminaryTimestamps.remove(myId);
                    } else {
                        preliminaryMap.put(myId, intents);
                    }
                }

                clock.log("[LOMET] Released resource: " + resId);
            }
        });
    }

    private boolean detectDeadlock() {
        Map<String, List<String>> adj = new HashMap<>();
        synchronized (globalWFG) {
            if (globalWFG.isEmpty()) return false;
            globalWFG.values().forEach(e ->
                    adj.computeIfAbsent(e.getFromId(), k -> new ArrayList<>()).add(e.getToId()));
        }
        for (String node : adj.keySet()) {
            if (findCycle(node, adj, new HashSet<>(), new LinkedHashSet<>()) != null) return true;
        }
        return false;
    }

    private void broadcastGlobalState() {
        Map<String, Object> state = new HashMap<>();
        state.put("wfg", new ArrayList<>(globalWFG.values()));
        state.put("holders", new HashMap<>(resourceHolders));
        state.put("preliminary", new HashMap<>(preliminaryMap));
        for (NodeInfo n : topology.getNeighbors()) {
            network.sendPost(n.getBaseUrl() + "/api/lomet/sync", state);
        }
    }

    private void addWaitEdge(String from, String to, String res) {
        globalWFG.put(from + "->" + to + ":" + res, new DependencyEdge(from, to, res, clock.getTime()));
    }

    private List<String> findCycle(String curr, Map<String, List<String>> adj, Set<String> visited, LinkedHashSet<String> stack) {
        if (stack.contains(curr)) return new ArrayList<>(stack);
        if (visited.contains(curr)) return null;
        visited.add(curr); stack.add(curr);
        List<String> neighbors = adj.get(curr);
        if (neighbors != null) {
            for (String n : neighbors) {
                List<String> res = findCycle(n, adj, visited, stack);
                if (res != null) return res;
            }
        }
        stack.remove(curr); return null;
    }

    public void handleNodeFailure(String deadNodeId) {
        synchronized (globalWFG) {
            // bypass logic - I think it is no need of it, but I will leave it here for assurance
//            clock.log("[LOMET] Emergency cleanup for failed node: " + deadNodeId);
//
//            List<String> targetsOfDeadNode = globalWFG.values().stream()
//                    .filter(e -> e.getFromId().equals(deadNodeId))
//                    .map(DependencyEdge::getToId)
//                    .collect(java.util.stream.Collectors.toList());
//
//            List<String> waitersOfDeadNode = globalWFG.values().stream()
//                    .filter(e -> e.getToId().equals(deadNodeId))
//                    .map(DependencyEdge::getFromId)
//                    .collect(java.util.stream.Collectors.toList());
//
//            for (String waiter : waitersOfDeadNode) {
//                for (String target : targetsOfDeadNode) {
//                    if (!waiter.equals(target)) {
//                        addWaitEdge(waiter, target);
//                        clock.log("[BYPASS] Node " + waiter + " now redirected to " + target);
//                    }
//                }
//            }

            globalWFG.entrySet().removeIf(e ->
                    e.getValue().getFromId().equals(deadNodeId) ||
                            e.getValue().getToId().equals(deadNodeId)
            );

            resourceHolders.entrySet().removeIf(entry -> entry.getValue().equals(deadNodeId));

            preliminaryMap.remove(deadNodeId);
        }
    }
}