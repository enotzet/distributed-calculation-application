package dsva.service;

import dsva.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class ComputationService {
    @Autowired private LogicalClockService logger;
    @Autowired private NetworkService network;
    @Autowired private LometService lomet;
    @Autowired private TopologyService topology;

    private int localWorkLoad = 0;
    private boolean isActive = false;
    private final Random random = new Random();

    public void initiateWork(int amount) {
        this.localWorkLoad = amount;
        this.isActive = true;
        lomet.executeInCS(() -> {
            lomet.removeAllWaitEdgesFrom(topology.getMyId());
        });
        logger.log("Calculation started. Local load: " + amount);
    }

    @Scheduled(fixedDelay = 2000)
    public void doWork() {
        if (!network.isOnline()) return;
        if (isActive && localWorkLoad > 0) {
            localWorkLoad--;
            logger.log("Working... lasts: " + localWorkLoad);
            if (localWorkLoad > 5 && random.nextInt(100) < 20)
                passWork();
            if (localWorkLoad == 0) {
                isActive = false;
                logger.log("Work ended. Node is IDLE.");
            }
        } else if (!isActive && !topology.getNeighbors().isEmpty()) {
            if (!isWaiting() && random.nextInt(100) < 10) {
                requestWorkFromNeighbor(topology.getRandomNeighbor().getId());
            }
        }
    }

    public void passWork() {
        if (localWorkLoad > 1) {
            int part = localWorkLoad / 2;
            localWorkLoad -= part;
            NodeInfo recipient = topology.getRandomNeighbor();
            if (recipient != null) {
                logger.log("giving " + part + " parts of work to node " + recipient.getId());
                network.sendPost(recipient.getBaseUrl() + "/api/work/receiveMessage",
                        new WorkUnit("task-" + System.currentTimeMillis(), part, topology.getMyId()));
            }
        }
    }

    public void requestWorkFromNeighbor(String targetId) {
        NodeInfo target = topology.getNeighborById(targetId);
        if (target == null) {
            logger.log("Error: Neighbor " + targetId + " is not in my topology!");
            return;
        }

        logger.log("Attempting to request work from " + targetId + ". Entering CS...");

        lomet.executeInCS(() -> {
            lomet.addWaitEdge(topology.getMyId(), targetId);
            logger.log("WFG Updated in CS: " + topology.getMyId() + " -> " + targetId);
        });

        network.sendPost(target.getBaseUrl() + "/api/work/request-grant", topology.getMyId());
    }

    public void receiveWork(WorkUnit unit) {
        this.localWorkLoad += unit.getLoad();
        this.isActive = true;
        lomet.executeInCS(() -> {
            lomet.removeWaitEdge(topology.getMyId(), unit.getSenderId());
        });
        logger.log("Work received. Status: ACTIVE.");
    }

    public void broadcastWFG() {
        List<DependencyEdge> edges = lomet.getGlobalWFG();
        for (NodeInfo n : topology.getNeighbors()) {
            network.sendPost(n.getBaseUrl() + "/api/lomet/sync", edges);
        }
    }

    private boolean isWaiting() {
        return lomet.getGlobalWFG().stream()
                .anyMatch(edge -> edge.getFromId().equals(topology.getMyId()));
    }

    public void setActive() {
        isActive = true;
        logger.log("Node is now ACTIVE");
    }

    public void setPassive() {
        this.isActive = false;
        logger.log("Node is now PASSIVE (Idle)");
    }

    public boolean isActive() { return isActive; }
}