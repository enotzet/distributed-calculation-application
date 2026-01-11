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
    private final Map<String, List<String>> preliminaryMap = new ConcurrentHashMap<>();

    private final ReentrantLock localLock = new ReentrantLock();

    public boolean executeInCS(Runnable action) {
        raService.requestCS();
        try {
            int timeout = 0;
            // Ждем до 40 секунд (80 * 500мс)
            while (!raService.isGranted() && timeout < 80) {
                Thread.sleep(500); // Спим дольше для стабильности на виртуалках
                timeout++;
            }
            if (raService.isGranted()) {
                raService.setInCS(true);
                action.run();
                broadcastGlobalState();
                raService.releaseCS();
                return true;
            } else {
                clock.log("[RA] FAILED: Timeout. Replies: " + raService.isGranted());
                raService.releaseCS();
                return false;
            }
        } catch (Exception e) {
            raService.releaseCS();
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public void sync(Map<String, Object> incoming) {
        synchronized (globalWFG) {
            // 1. Синхронизация графа (Полная перезапись)
            List<Map<String, Object>> wfgList = (List<Map<String, Object>>) incoming.get("wfg");
            globalWFG.clear();
            if (wfgList != null) {
                for (Map<String, Object> e : wfgList) {
                    DependencyEdge edge = new DependencyEdge(
                            (String)e.get("fromId"),
                            (String)e.get("toId"),
                            ((Number)e.get("logicalTime")).longValue()
                    );
                    globalWFG.put(edge.getFromId() + "->" + edge.getToId(), edge);
                }
            }
            // 2. Синхронизация владельцев
            Map<String, String> holders = (Map<String, String>) incoming.get("holders");
            resourceHolders.clear();
            if (holders != null) resourceHolders.putAll(holders);

            // 3. Синхронизация намерений
            Map<String, List<String>> prelim = (Map<String, List<String>>) incoming.get("preliminary");
            preliminaryMap.clear();
            if (prelim != null) preliminaryMap.putAll(prelim);
        }
    }

    public void sendPreliminaryRequests(List<String> resources) {
        String myId = topology.getMyId();
        executeInCS(() -> {
            clock.log("[LOMET] Preliminary requests for: " + resources);
            preliminaryMap.put(myId, resources);

            // Если ресурсы уже кем-то заняты, сразу строим зависимости
            for (String res : resources) {
                String holder = resourceHolders.get(res);
                if (holder != null && !holder.equals(myId)) {
                    addWaitEdge(myId, holder);
                }
            }
        });
    }

    public boolean acquireResource(String resId) {
        String myId = topology.getMyId();
        final boolean[] success = {true};

        boolean entered = executeInCS(() -> {
            clock.log("[LOMET] Attempting to acquire: " + resId);
            Map<String, DependencyEdge> backupWFG = new HashMap<>(globalWFG);
            Map<String, String> backupHolders = new HashMap<>(resourceHolders);

            resourceHolders.put(resId, myId);
            preliminaryMap.forEach((waiterId, wantedResources) -> {
                if (!waiterId.equals(myId) && wantedResources.contains(resId)) {
                    addWaitEdge(waiterId, myId);
                }
            });

            if (detectDeadlock()) {
                clock.log("!!! DEADLOCK DETECTED !!! Rolling back " + resId);
                globalWFG.clear();
                globalWFG.putAll(backupWFG);
                resourceHolders.clear();
                resourceHolders.putAll(backupHolders);
                success[0] = false;
            } else {
                clock.log("[LOMET] " + resId + " acquired successfully.");
            }
        });

        return entered && success[0];
    }

    public void releaseResource(String resId) {
        executeInCS(() -> {
            internalReleaseLogic(resId);
        });
    }

    private void internalReleaseLogic(String resId) {
        resourceHolders.remove(resId);
        globalWFG.entrySet().removeIf(e -> e.getValue().getToId().equals(topology.getMyId()));
        clock.log("[LOMET] Released resource: " + resId);
    }

    private boolean detectDeadlock() {
        Map<String, List<String>> adj = new HashMap<>();
        synchronized (globalWFG) {
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

    private void addWaitEdge(String from, String to) {
        globalWFG.put(from + "->" + to, new DependencyEdge(from, to, clock.tick()));
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
            List<String> targetsOfDeadNode = globalWFG.values().stream()
                    .filter(e -> e.getFromId().equals(deadNodeId))
                    .map(DependencyEdge::getToId)
                    .collect(java.util.stream.Collectors.toList());

            List<String> waitersOfDeadNode = globalWFG.values().stream()
                    .filter(e -> e.getToId().equals(deadNodeId))
                    .map(DependencyEdge::getFromId)
                    .collect(java.util.stream.Collectors.toList());

            for (String waiter : waitersOfDeadNode) {
                for (String target : targetsOfDeadNode) {
                    if (!waiter.equals(target)) {
                        addWaitEdge(waiter, target);
                        clock.log("[BYPASS] Node " + waiter + " now waiting for " + target);
                    }
                }
            }

            globalWFG.entrySet().removeIf(e -> e.getValue().getFromId().equals(deadNodeId) || e.getValue().getToId().equals(deadNodeId));
            resourceHolders.entrySet().removeIf(e -> e.getValue().equals(deadNodeId));
        }
    }
}