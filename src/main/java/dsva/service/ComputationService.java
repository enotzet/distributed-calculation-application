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
        logger.log("Calculation started. Local load: " + amount);
        checkAndGrantPendingRequests();
    }

    @Scheduled(fixedDelay = 2000)
    public void doWork() {
        if (!network.isOnline()) return;
        if (isActive && localWorkLoad > 0) {
            localWorkLoad--;
            logger.log("Working... lasts: " + localWorkLoad);
            checkAndGrantPendingRequests();
            if (localWorkLoad > 5 && random.nextInt(100) < 20)
                //passWork(null);
            if (localWorkLoad == 0) {
                isActive = false;
                logger.log("Work ended. Node is IDLE.");
            }
        } else if (!isActive && !topology.getNeighbors().isEmpty()) {
            if (!isWaiting() && random.nextInt(100) < 10) {
                // requestWorkFromNeighbor(topology.getRandomNeighbor().getId());
            }
        }
    }

    public void passWork(String specificRecipientId) {
        if (localWorkLoad > 1) {
            int part = localWorkLoad / 2;
            NodeInfo recipient = null;

            if (specificRecipientId != null && !specificRecipientId.isEmpty()) {
                recipient = topology.getNeighborById(specificRecipientId);
            }

            if (recipient == null) {
                recipient = topology.getRandomNeighbor();
            }

            if (recipient != null) {
                localWorkLoad -= part;
                logger.log("Giving " + part + " parts of work to node " + recipient.getId());
                network.sendPost(recipient.getBaseUrl() + "/api/work/receiveMessage",
                        new WorkUnit("task-" + System.currentTimeMillis(), part, topology.getMyId()));
            } else {
                logger.log("No neighbors available to pass work.");
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

    private void checkAndGrantPendingRequests() {
        if (localWorkLoad <= 1) return;
        List<String> waiters = lomet.getGlobalWFG().stream()
                .filter(edge -> edge.getToId().equals(topology.getMyId()))
                .map(DependencyEdge::getFromId)
                .collect(java.util.stream.Collectors.toList());

        for (String requesterId : waiters) {
            if (localWorkLoad > 1) {
                lomet.removeWaitEdge(requesterId, topology.getMyId());

                logger.log("Detected that " + requesterId + " is still waiting for me. Granting work now.");
                passWork(requesterId);

                lomet.executeInCS(() -> {
                    lomet.removeWaitEdge(requesterId, topology.getMyId());
                });
            }
        }
    }

    public void receiveWork(WorkUnit unit) {
        this.localWorkLoad += unit.getLoad();
        this.isActive = true;
        lomet.executeInCS(() -> {
            lomet.removeWaitEdge(topology.getMyId(), unit.getSenderId());
        });
        logger.log("Work received. Status: ACTIVE.");
        checkAndGrantPendingRequests();
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