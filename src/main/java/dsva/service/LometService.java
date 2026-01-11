package dsva.service;

import dsva.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class LometService {
    @Autowired private LogicalClockService clock;
    @Autowired private RicartAgrawalaService raService;
    @Autowired private TopologyService topology;
    @Autowired private NetworkService network;

    private final Map<String, DependencyEdge> globalWFG = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, String> resourceHolders = Collections.synchronizedMap(new HashMap<>());

    public void executeInCS(Runnable action) {
        raService.requestCS();
        try {
            int timeout = 0;
            while (!raService.isGranted() && timeout < 50) {
                Thread.sleep(200);
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

    public void sendPreliminaryRequests(List<String> resources) {
        String myId = topology.getMyId();
        executeInCS(() -> {
            clock.log("[LOMET] Preliminary requests for: " + resources);
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

        executeInCS(() -> {
            resourceHolders.put(resId, myId);
            clock.log("[LOMET] Acquired resource: " + resId + ". Checking for deadlock (aposteriori)...");
        });

        if (detectDeadlock()) {
            clock.log("!!! DEADLOCK DETECTED !!! Releasing resource " + resId + " to break cycle.");
            releaseResource(resId);
            return false;
        }
        return true;
    }

    public void releaseResource(String resId) {
        executeInCS(() -> {
            resourceHolders.remove(resId);
            globalWFG.entrySet().removeIf(e -> e.getValue().getToId().equals(topology.getMyId()));
            clock.log("[LOMET] Released resource: " + resId);
        });
    }

    private boolean detectDeadlock() {
        Map<String, List<String>> adj = new HashMap<>();
        synchronized (globalWFG) {
            globalWFG.values().forEach(e -> adj.computeIfAbsent(e.getFromId(), k -> new ArrayList<>()).add(e.getToId()));
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
        for (NodeInfo n : topology.getNeighbors()) {
            network.sendPost(n.getBaseUrl() + "/api/lomet/sync", state);
        }
    }

    public void sync(Map<String, Object> incoming) {
        synchronized (globalWFG) {
            List<Map<String, Object>> wfgList = (List<Map<String, Object>>) incoming.get("wfg");
            globalWFG.clear();
            for (Map<String, Object> e : wfgList) {
                String key = e.get("fromId") + "->" + e.get("toId");
                globalWFG.put(key, new DependencyEdge((String)e.get("fromId"), (String)e.get("toId"), ((Number)e.get("logicalTime")).longValue()));
            }
            resourceHolders.clear();
            resourceHolders.putAll((Map<String, String>) incoming.get("holders"));
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
}