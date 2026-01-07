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

        while (attempts < 4 && !success) {
            String leaderId = topology.getLeaderId();
            if (topology.isLeader()) {
                clock.log("[CS] I am the Leader. Executing.");
                action.run();
                broadcastWFG();
                success = true;
            } else {
                boolean granted = network.requestLockFromLeader(leaderId);
                if (granted) {
                    action.run();
                    broadcastWFG();
                    network.releaseLockOnLeader(leaderId);
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

    public void syncEdges(List<DependencyEdge> incoming) {
        synchronized (globalWFG) {
            globalWFG.clear();
            for (DependencyEdge e : incoming) {
                String key = e.getFromId() + "->" + e.getToId();
                globalWFG.put(key, e);
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
        if (cycle.contains(myId)) {
            clock.log("[RESOLUTION] I am part of the deadlock. Aborting my request to break the cycle.");

            int myIndex = cycle.indexOf(myId);
            int nextIndex = (myIndex + 1) % cycle.size();
            String waitingFor = cycle.get(nextIndex);

            globalWFG.remove(myId + "->" + waitingFor);
            broadcastWFG();

            clock.log("[RESOLUTION] Edge " + myId + " -> " + waitingFor + " removed. Deadlock broken.");
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