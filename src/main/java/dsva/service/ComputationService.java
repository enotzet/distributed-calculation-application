package dsva.service;

import dsva.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class ComputationService {
//    @Autowired private LogicalClockService logger;
//    @Autowired private NetworkService network;
//    @Autowired private LometService lomet;
//    @Autowired private TopologyService topology;
//
//    private int localWorkLoad = 0;
//    private boolean isActive = false;
//    private final Random random = new Random();
//
//    private final Map<String, Long> lastGrantTimes = new HashMap<>();
//
//
//    public void initiateWork(int amount) {
//        this.localWorkLoad = amount;
//        this.isActive = true;
//        logger.log("Calculation started. Local load: " + amount);
//        checkAndGrantPendingRequests();
//    }
//
//    @Scheduled(fixedDelay = 2000)
//    public void doWork() {
//        if (!network.isOnline()) return;
//        if (isActive && localWorkLoad > 0) {
//            localWorkLoad--;
//            logger.log("Working... lasts: " + localWorkLoad);
//            checkAndGrantPendingRequests();
//            if (localWorkLoad > 5 && random.nextInt(100) < 20)
//                //passWork(null);
//            if (localWorkLoad == 0) {
//                isActive = false;
//                logger.log("Work ended. Node is IDLE.");
//            }
//        } else if (!isActive && !topology.getNeighbors().isEmpty()) {
//            if (!isWaiting() && random.nextInt(100) < 10) {
//                // requestWorkFromNeighbor(topology.getRandomNeighbor().getId());
//            }
//        }
//    }
//
//    public void passWork(String specificRecipientId) {
//        if (localWorkLoad > 1) {
//            int part = localWorkLoad / 2;
//            NodeInfo recipient = null;
//
//            if (specificRecipientId != null && !specificRecipientId.isEmpty()) {
//                recipient = topology.getNeighborById(specificRecipientId);
//            }
//
//            if (recipient == null) {
//                recipient = topology.getRandomNeighbor();
//            }
//
//            if (recipient != null) {
//                localWorkLoad -= part;
//                logger.log("Giving " + part + " parts of work to node " + recipient.getId());
//                network.sendPost(recipient.getBaseUrl() + "/api/work/receiveMessage",
//                        new WorkUnit("task-" + System.currentTimeMillis(), part, topology.getMyId()));
//            } else {
//                logger.log("No neighbors available to pass work.");
//            }
//        }
//    }
//    public void requestWorkWorkflow(String targetId) {
//        new Thread(() -> {
//            try {
//                String myId = topology.getMyId();
//
//                // 1. Preliminary Request: Объявляем намерение в WFG
//                // (Уже добавлено через API или вызываем здесь)
//                lomet.preliminaryRequest(myId, targetId);
//                Thread.sleep(1000); // Даем время на синхронизацию графа
//
//                // 2. Request Phase: Проверка на дедлок (Prevention)
//                logger.log("[WORKFLOW] Validating request to " + targetId);
//                if (lomet.validateRequest(myId, targetId)) {
//
//                    // 3. Acquire Phase: Получаем ресурс (запрашиваем работу)
//                    NodeInfo target = topology.getNeighborById(targetId);
//                    if (target != null) {
//                        logger.log("[WORKFLOW] ACQUIRE: Sending real request to " + targetId);
//                        network.sendPost(target.getBaseUrl() + "/api/work/request-grant", myId);
//                    }
//                } else {
//                    logger.log("[WORKFLOW] ABORT: Request denied to prevent Deadlock.");
//                }
//
//            } catch (Exception e) { e.printStackTrace(); }
//        }).start();
//    }
//
//    public void receiveWork(WorkUnit unit) {
//        this.localWorkLoad += unit.getLoad();
//        this.isActive = true;
//        logger.log("Work received. Status: ACTIVE. Releasing edge...");
//
//        lomet.executeInCS(() -> {
//            lomet.removeWaitEdge(topology.getMyId(), unit.getSenderId());
//        });
//
//        checkAndGrantPendingRequests();
//    }
//
//    public void requestWorkFromNeighbor(String targetId) {
//        NodeInfo target = topology.getNeighborById(targetId);
//        if (target == null) {
//            logger.log("Error: Neighbor " + targetId + " is not in my topology!");
//            return;
//        }
//
//        logger.log("Attempting to request work from " + targetId + ". Entering CS...");
//
//        lomet.executeInCS(() -> {
//            lomet.addWaitEdge(topology.getMyId(), targetId);
//            logger.log("WFG Updated in CS: " + topology.getMyId() + " -> " + targetId);
//        });
//
//        network.sendPost(target.getBaseUrl() + "/api/work/request-grant", topology.getMyId());
//    }
//
//    private synchronized void checkAndGrantPendingRequests() {
//        if (localWorkLoad <= 1) return;
//
//        String myId = topology.getMyId();
//        List<DependencyEdge> currentEdges = lomet.getGlobalWFG();
//
//        for (DependencyEdge edge : currentEdges) {
//            if (edge.getToId().equals(myId)) {
//                String requesterId = edge.getFromId();
//                long now = System.currentTimeMillis();
//
//                if (lastGrantTimes.getOrDefault(requesterId, 0L) > now - 10000) {
//                    continue;
//                }
//
//                lastGrantTimes.put(requesterId, now);
//
//                logger.log("Detected waiter " + requesterId + ". Granting work...");
//                passWork(requesterId);
//
//                lomet.removeWaitEdge(requesterId, myId);
//
//                lomet.executeInCS(() -> {
//                    lomet.removeWaitEdge(requesterId, myId);
//                });
//
//                break;
//            }
//        }
//    }
//
//    public void broadcastWFG() {
//        List<DependencyEdge> edges = lomet.getGlobalWFG();
//        for (NodeInfo n : topology.getNeighbors()) {
//            network.sendPost(n.getBaseUrl() + "/api/lomet/sync", edges);
//        }
//    }
//
//    private boolean isWaiting() {
//        return lomet.getGlobalWFG().stream()
//                .anyMatch(edge -> edge.getFromId().equals(topology.getMyId()));
//    }
}