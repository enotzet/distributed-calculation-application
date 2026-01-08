package dsva.service;

import dsva.model.DependencyEdge;
import dsva.model.NodeInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class LometService {
    @Autowired
    private LogicalClockService clock;

    @Autowired
    private TopologyService topology;

    @Autowired
    private NetworkService network;

    private final Map<String, DependencyEdge> globalWFG = Collections.synchronizedMap(new HashMap<>());

    public void executeInCS(Runnable action) {
        int attempts = 0;
        boolean success = false;

        while (attempts < 10 && !success) {
            String leaderId = topology.getLeaderId();
            if (topology.isLeader()) {
                clock.log("[CS] I am the Leader. Executing.");
                action.run();
                broadcastWFG();
                checkDeadlock();
                success = true;
            } else {
                boolean granted = network.requestLockFromLeader(leaderId);
                if (granted) {
                    action.run();
                    broadcastWFG();
                    network.releaseLockOnLeader(leaderId);

                    checkDeadlock();

                    success = true;
                } else {
                    attempts++;
                    clock.log("Leader " + leaderId + " denied or dead. Retry " + attempts);
                    try { Thread.sleep(500); } catch (InterruptedException e) {}
                }
            }
        }
    }

    public void addWaitEdge(String from, String to) {
        String key = from + "->" + to;
        globalWFG.put(key, new DependencyEdge(from, to, clock.tick()));
    }

    public void removeWaitEdge(String from, String to) {
        globalWFG.remove(from + "->" + to);
    }

    public void broadcastWFG() {
        List<DependencyEdge> edges = new ArrayList<>(globalWFG.values());
        for ( NodeInfo n : topology.getNeighbors()) {
            network.sendPost(n.getBaseUrl() + "/api/lomet/sync", edges);
        }
    }

    public void removeEdgesInvolving(String nodeId) {
        synchronized (globalWFG) {
            globalWFG.entrySet().removeIf(entry ->
                    entry.getValue().getFromId().equals(nodeId) ||
                            entry.getValue().getToId().equals(nodeId)
            );
        }
        clock.log("Cleaned up WFG edges involving: " + nodeId);
        broadcastWFG();
    }

    public void syncEdges(List<DependencyEdge> incoming) {
        synchronized (globalWFG) {
            globalWFG.clear();
            for (DependencyEdge e : incoming) {
                globalWFG.put(e.getFromId() + "->" + e.getToId(), e);
            }
        }
        if (!incoming.isEmpty()) {
            checkDeadlock();
        }
    }


    public void removeAllWaitEdgesFrom(String fromId) {
        globalWFG.entrySet().removeIf(entry -> entry.getValue().getFromId().equals(fromId));
        clock.log("Deleted all edges from node: " + fromId);
    }

    public void checkDeadlock() {
        if ( !network.isOnline() ) return;
        Map<String, List<String>> adj = new HashMap<>();
        synchronized (globalWFG) {
            for (DependencyEdge edge : globalWFG.values()) {
                adj.computeIfAbsent(edge.getFromId(), k -> new ArrayList<>()).add(edge.getToId());
            }
        }

        Set<String> visited = new HashSet<>();
        for (String node : adj.keySet()) {
            if (!visited.contains(node)) {
                LinkedHashSet<String> stack = new LinkedHashSet<>();
                List<String> cycle = findCycle(node, adj, visited, stack);
                if (cycle != null) {
                    clock.log("!!! DEADLOCK DETECTED !!! Cycle: " + String.join(" -> ", cycle) + " -> " + cycle.get(0));
                    resolveDeadlock(cycle);
                    return;
                }
            }
        }
    }

    private void resolveDeadlock(List<String> cycle) {
        String myId = topology.getMyId();
        DependencyEdge latestEdge = null;

        synchronized (globalWFG) {
            for (int i = 0; i < cycle.size(); i++) {
                String from = cycle.get(i);
                String to = cycle.get((i + 1) % cycle.size());
                String key = from + "->" + to;

                DependencyEdge edge = globalWFG.get(key);
                if (edge != null) {
                    if (latestEdge == null || edge.getLogicalTime() > latestEdge.getLogicalTime()) {
                        latestEdge = edge;
                    }
                }
            }
        }

        if (latestEdge == null) return;

        if (latestEdge.getFromId().equals(myId)) {
            clock.log("[RESOLUTION] I own the latest edge in the cycle (" + latestEdge.getLogicalTime() +
                    "). Breaking the deadlock.");

            String finalTo = latestEdge.getToId();
            executeInCS(() -> {
                removeWaitEdge(myId, finalTo);
                clock.log("[RESOLUTION] Deadlock resolved by removing latest edge: " + myId + " -> " + finalTo);
            });
        } else {
            clock.log("[RESOLUTION] Deadlock detected, but it's not my turn to resolve. Waiting for " + latestEdge.getFromId());
        }
    }

    private List<String> findCycle(String curr, Map<String, List<String>> adj, Set<String> visited, LinkedHashSet<String> stack) {
        if (stack.contains(curr)) {
            List<String> fullStack = new ArrayList<>(stack);
            int cycleStartIndex = fullStack.indexOf(curr);
            return new ArrayList<>(fullStack.subList(cycleStartIndex, fullStack.size()));
        }

        if (visited.contains(curr)) return null;

        visited.add(curr);
        stack.add(curr);

        List<String> neighbors = adj.get(curr);
        if (neighbors != null) {
            for (String neighbor : neighbors) {
                List<String> cycle = findCycle(neighbor, adj, visited, stack);
                if (cycle != null) return cycle;
            }
        }

        stack.remove(curr);
        return null;
    }

    public List<DependencyEdge> getGlobalWFG() {
        synchronized (globalWFG) {
            return new ArrayList<>(globalWFG.values());
        }
    }
}