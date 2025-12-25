package dsva.service;

import dsva.model.NodeInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
public class TopologyService {

    @Value("${server.port}")
    private int port;

    private final String host = "localhost";

    private final List<NodeInfo> neighbors = Collections.synchronizedList(new ArrayList<>());

    @Autowired
    private LogicalClockService logger;

    public String getMyId() {
        return host + ":" + port;
    }

    public String getMyUrl() {
        return "http://" + host + ":" + port;
    }

    public void addNeighbor(NodeInfo node) {
        if (!node.getId().equals(getMyId()) && !neighbors.contains(node)) {
            neighbors.add(node);
            logger.log("Neighbour added: " + node.getId() + ". Count of neighbours: " + neighbors.size());
        }
    }

    public void removeNeighbor(String nodeId) {
        neighbors.removeIf(n -> n.getId().equals(nodeId));
        logger.log("Neighbour is removed: " + nodeId);
    }

    public List<NodeInfo> getNeighbors() {
        return new ArrayList<>(neighbors);
    }

    public NodeInfo getRandomNeighbor() {
        if (neighbors.isEmpty()) return null;
        return neighbors.get(new Random().nextInt(neighbors.size()));
    }
}