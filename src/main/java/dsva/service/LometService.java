package dsva.service;

import dsva.model.DependencyEdge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class LometService {
    @Autowired private LogicalClockService logger;

    // Set hran v globálním Wait-For Graphu
    private final Set<DependencyEdge> globalWFG = Collections.synchronizedSet(new HashSet<>());

    // Metoda pro přidání jedné konkrétní hrany (používá ComputationService při žádosti o práci)
    public void addWaitEdge(String fromId, String toId) {
        DependencyEdge newEdge = new DependencyEdge(fromId, toId, logger.getTime());
        globalWFG.add(newEdge);
        logger.log("Wait edge is added : " + fromId + " -> " + toId);
        checkDeadlock();
    }

    // Metoda pro odebrání hrany (používá ComputationService, když uzel dostane práci a přestane čekat)
    public void removeWaitEdge(String fromId, String toId) {
        boolean removed = globalWFG.removeIf(edge ->
                edge.getFromId().equals(fromId) && edge.getToId().equals(toId));

        if (removed) {
            logger.log("HWait edge is removed: " + fromId + " -> " + toId);
        }
    }

    // Alternativní metoda pro odebrání všech hran, kde uzel figuruje jako čekající (From)
    public void removeAllWaitEdgesFrom(String fromId) {
        globalWFG.removeIf(edge -> edge.getFromId().equals(fromId));
        logger.log("Deleted all waited edges from node: " + fromId);
    }

    // Tato metoda už v kódu byla - slouží pro synchronizaci grafu mezi uzly
    public void addEdges(List<DependencyEdge> newEdges) {
        globalWFG.addAll(newEdges);
        checkDeadlock();
    }

    public void checkDeadlock() {
        // Logika pro detekci cyklu (ponech stejnou, jakou jsi měl)
        Map<String, List<String>> adj = new HashMap<>();
        synchronized (globalWFG) {
            for (DependencyEdge edge : globalWFG) {
                adj.computeIfAbsent(edge.getFromId(), k -> new ArrayList<>()).add(edge.getToId());
            }
        }

        for (String node : adj.keySet()) {
            if (hasCycle(node, adj, new HashSet<>(), new HashSet<>())) {
                logger.log("!!! DEADLOCK IS DETECTED !!!");
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
        synchronized (globalWFG) {
            return new ArrayList<>(globalWFG);
        }
    }
}