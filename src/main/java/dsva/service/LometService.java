package dsva.service;

import dsva.model.DependencyEdge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LometService {
    @Autowired
    private LogicalClockService clock;

    private final Map<String, DependencyEdge> globalWFG = Collections.synchronizedMap(new HashMap<>());

    private final Map<String, Long> lastNodeUpdateClock = Collections.synchronizedMap(new HashMap<>());

    private String getEdgeKey(String from, String to) {
        return from + "->" + to;
    }

    public void addWaitEdge(String fromId, String toId) {
        long currentTime = clock.tick();
        DependencyEdge newEdge = new DependencyEdge(fromId, toId, currentTime);
        globalWFG.put(getEdgeKey(fromId, toId), newEdge);
        clock.log("Wait edge added: " + fromId + " -> " + toId);
        checkDeadlock();
    }

    public void removeWaitEdge(String fromId, String toId) {
        if (globalWFG.remove(getEdgeKey(fromId, toId)) != null) {
            clock.log("Wait edge removed: " + fromId + " -> " + toId);
        }
    }

    public void removeAllWaitEdgesFrom(String fromId) {
        globalWFG.entrySet().removeIf(entry -> entry.getValue().getFromId().equals(fromId));
        clock.log("Deleted all edges from node: " + fromId);
    }

    public void addEdges(String senderId, List<DependencyEdge> incomingEdges, long remoteClock) {
        synchronized (globalWFG) {
            lastNodeUpdateClock.put(senderId, remoteClock);

            if (incomingEdges.isEmpty()) {
                clock.log("Node " + senderId + " is no longer waiting for anyone. Updating global WFG.");
            }

            globalWFG.entrySet().removeIf(entry ->
                    entry.getValue().getFromId().equals(senderId) && incomingEdges.stream().noneMatch(e -> e.getToId().equals(entry.getValue().getToId()))
            );

            for (DependencyEdge incoming : incomingEdges) {
                String key = getEdgeKey(incoming.getFromId(), incoming.getToId());
                DependencyEdge existing = globalWFG.get(key);
                if (existing == null || incoming.getLogicalTime() > existing.getLogicalTime()) {
                    globalWFG.put(key, incoming);
                }
            }
        }
        checkDeadlock();
    }

    public void checkDeadlock() {
        Map<String, List<String>> adj = new HashMap<>();
        synchronized (globalWFG) {
            for (DependencyEdge edge : globalWFG.values()) {
                adj.computeIfAbsent(edge.getFromId(), k -> new ArrayList<>()).add(edge.getToId());
            }
        }

        for (String node : adj.keySet()) {
            if (hasCycle(node, adj, new HashSet<>(), new LinkedHashSet<>())) {
                clock.log("!!! DEADLOCK DETECTED !!! Nodes in cycle: " + node);
                return;
            }
        }
    }

    private boolean hasCycle(String curr, Map<String, List<String>> adj, Set<String> visited, Set<String> stack) {
        if (stack.contains(curr)) return true;
        if (visited.contains(curr)) return false;
        visited.add(curr);
        stack.add(curr);
        List<String> neighbors = adj.get(curr);
        if (neighbors != null) {
            for (String neighbor : neighbors) {
                if (hasCycle(neighbor, adj, visited, stack)) return true;
            }
        }
        stack.remove(curr);
        return false;
    }

    public List<DependencyEdge> getGlobalWFG() {
        return new ArrayList<>(globalWFG.values());
    }
}