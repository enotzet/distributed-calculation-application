package dsva.service;

import dsva.model.NodeInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RicartAgrawalaService {
    @Autowired private TopologyService topology;
    @Autowired private LogicalClockService clock;
    @Autowired @Lazy private NetworkService network;

    private boolean requestingCS = false;
    private long requestTime = 0;
    private final List<String> deferredReplies = new ArrayList<>();
    private final AtomicInteger repliesCount = new AtomicInteger(0);
    private boolean inCS = false;

    public synchronized void requestCS() {
        requestingCS = true;
        inCS = false;
        repliesCount.set(0);
        requestTime = clock.tick();
        deferredReplies.clear();

        List<NodeInfo> neighbors = topology.getNeighbors();
        if (neighbors.isEmpty()) { inCS = true; return; }

        clock.log("[RA] Requesting CS for WFG update. Time: " + requestTime);
        for (NodeInfo n : neighbors) {
            network.sendRaRequest(n.getBaseUrl(), requestTime, topology.getMyId());
        }
    }

    public synchronized void onReceiveRequest(long remoteTime, String senderId) {
        clock.update(remoteTime);
        // Приоритет: меньше метка времени -> меньше ID (IP:Port)
        boolean myPriority = requestingCS &&
                (requestTime < remoteTime || (requestTime == remoteTime && topology.getMyId().compareTo(senderId) < 0));

        if (inCS || myPriority) {
            clock.log("[RA] Deferring reply to " + senderId);
            deferredReplies.add(senderId);
        } else {
            network.sendRaReply(senderId);
        }
    }

    public synchronized void onReceiveReply() {
        repliesCount.incrementAndGet();
    }

    public synchronized boolean isGranted() {
        return repliesCount.get() >= topology.getNeighbors().size();
    }

    public synchronized void releaseCS() {
        inCS = false;
        requestingCS = false;
        for (String nodeId : deferredReplies) {
            network.sendRaReply(nodeId);
        }
        deferredReplies.clear();
    }

    public void setInCS(boolean val) { this.inCS = val; }
}