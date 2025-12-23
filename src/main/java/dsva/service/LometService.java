package dsva.service;

import dsva.model.DependencyEdge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class LometService {
    @Autowired private LogicalClockService logger;
    private final Set<DependencyEdge> globalWFG = Collections.synchronizedSet(new HashSet<>());

    public void addEdges(List<DependencyEdge> newEdges) {
        globalWFG.addAll(newEdges);
        checkDeadlock();
    }

    private void checkDeadlock() {
        Map<String, List<String>> adj = new HashMap<>();
        for (DependencyEdge edge : globalWFG) {
            adj.computeIfAbsent(edge.getFromId(), k -> new ArrayList<>()).add(edge.getToId());
        }

        for (String node : adj.keySet()) {
            if (hasCycle(node, adj, new HashSet<>(), new HashSet<>())) {
                logger.log("!!! DETEKVÁNO UVÁZNUTÍ (DEADLOCK) V GRAFU !!!");
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

    public List<DependencyEdge> getGlobalWFG() { return new ArrayList<>(globalWFG); }
}
