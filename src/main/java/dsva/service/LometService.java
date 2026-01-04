package dsva.service;

import dsva.model.DependencyEdge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

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

    public void removeNodeCompletely(String nodeId) {
        synchronized (globalWFG) {
            globalWFG.entrySet().removeIf(entry ->
                    entry.getValue().getFromId().equals(nodeId) ||
                            entry.getValue().getToId().equals(nodeId)
            );
        }
        clock.log("Lomet graph cleaned for node: " + nodeId);
    }

    public void removeAllWaitEdgesFrom(String fromId) {
        globalWFG.entrySet().removeIf(entry -> entry.getValue().getFromId().equals(fromId));
        clock.log("Deleted all edges from node: " + fromId);
    }

    public void addEdges(String senderId, List<DependencyEdge> incomingEdges, long remoteClock) {
        synchronized (globalWFG) {
            lastNodeUpdateClock.put(senderId, remoteClock);

            globalWFG.entrySet().removeIf(entry ->
                    entry.getValue().getFromId().equals(senderId) &&
                            incomingEdges.stream().noneMatch(e -> e.getToId().equals(entry.getValue().getToId()))
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

        Set<String> visited = new HashSet<>();
        for (String node : adj.keySet()) {
            if (!visited.contains(node)) {
                LinkedHashSet<String> stack = new LinkedHashSet<>();
                List<String> cycle = findCycle(node, adj, visited, stack);
                if (cycle != null) {
                    clock.log("!!! DEADLOCK DETECTED !!! Cycle: " + String.join(" -> ", cycle) + " -> " + cycle.get(0));
                    return;
                }
            }
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