// file: src/main/java/dsva/service/ComputationService.java
package dsva.service;

import dsva.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
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
        logger.log("Výpočet zahájen. Lokální zátěž: " + amount);
    }

    // Automatický proces výpočtu (běží každé 2 sekundy)
    @Scheduled(fixedDelay = 2000)
    public void doWork() {
        if (isActive && localWorkLoad > 0) {
            localWorkLoad--;
            logger.log("Pracuji... zbývá: " + localWorkLoad);

            // Náhodně zkusíme předat práci dál (simulace distribuce)
            if (localWorkLoad > 5 && random.nextInt(100) < 20) {
                passWork();
            }

            if (localWorkLoad == 0) {
                isActive = false;
                logger.log("Práce dokončena. Uzel je IDLE.");
            }
        } else if (!isActive && !topology.getNeighbors().isEmpty() && random.nextInt(100) < 10) {
            // Pokud nic nedělám, občas si zažádám o práci (vytváří hrany pro Lomet)
            requestWorkFromNeighbor();
        }
    }

    public void passWork() {
        if (localWorkLoad > 1) {
            int part = localWorkLoad / 2;
            localWorkLoad -= part;
            NodeInfo recipient = topology.getRandomNeighbor();
            if (recipient != null) {
                logger.log("Předávám " + part + " jednotek práce uzlu " + recipient.getId());
                network.sendPost(recipient.getBaseUrl() + "/api/work/receive",
                        new WorkUnit("task-" + System.currentTimeMillis(), part, topology.getMyId()));
            }
        }
    }

    public void requestWorkFromNeighbor() {
        NodeInfo target = topology.getRandomNeighbor();
        if (target != null) {
            logger.log("Žádám o práci u " + target.getId());
            // Lomet: Přidáme hranu a musíme ji rozeslat všem (globální algoritmus)
            lomet.addWaitEdge(topology.getMyId(), target.getId());
            broadcastWFG();
            network.sendPost(target.getBaseUrl() + "/api/work/request-grant", topology.getMyId());
        }
    }

    public void receiveWork(WorkUnit unit) {
        this.localWorkLoad += unit.getLoad();
        this.isActive = true;
        // Přestáváme čekat na uzel, který nám poslal práci
        lomet.removeWaitEdge(topology.getMyId(), unit.getSenderId());
        broadcastWFG();
        logger.log("Přijata práce (" + unit.getLoad() + ") od " + unit.getSenderId());
    }

    private void broadcastWFG() {
        for (NodeInfo n : topology.getNeighbors()) {
            network.sendPost(n.getBaseUrl() + "/api/lomet/update", lomet.getGlobalWFG());
        }
    }

    public boolean isActive() { return isActive; }
}