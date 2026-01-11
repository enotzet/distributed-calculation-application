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
    private long requestTime = Long.MAX_VALUE;
    private final Set<String> deferredReplies = Collections.synchronizedSet(new HashSet<>());
    private final AtomicInteger repliesCount = new AtomicInteger(0);
    private boolean inCS = false;

    public synchronized void requestCS() {
        this.requestingCS = true;
        this.inCS = false;
        this.repliesCount.set(0);
        this.requestTime = clock.tick();
        this.deferredReplies.clear();

        List<NodeInfo> neighbors = topology.getNeighbors();
        if (neighbors.isEmpty()) {
            this.inCS = true;
            return;
        }

        // Рассылаем запросы
        for (NodeInfo n : neighbors) {
            network.sendRaRequest(n.getBaseUrl(), this.requestTime, topology.getMyId());
        }
    }

    public synchronized void onReceiveRequest(long remoteTime, String senderId) {
        clock.update(remoteTime);

        // Формула приоритета
        boolean myPriority = requestingCS &&
                (this.requestTime < remoteTime || (this.requestTime == remoteTime && topology.getMyId().compareTo(senderId) < 0));

        if (inCS || myPriority) {
            deferredReplies.add(senderId);
        } else {
            network.sendRaReply(senderId);
        }
    }

    public synchronized void onReceiveReply() {
        repliesCount.incrementAndGet();
    }

    public synchronized boolean isGranted() {
        int neighborsCount = topology.getNeighbors().size();
        return neighborsCount == 0 || repliesCount.get() >= neighborsCount;
    }

    public synchronized void releaseCS() {
        this.inCS = false;
        this.requestingCS = false;
        this.requestTime = Long.MAX_VALUE;
        synchronized (deferredReplies) {
            for (String nodeId : deferredReplies) {
                network.sendRaReply(nodeId);
            }
            deferredReplies.clear();
        }
    }

    public void setInCS(boolean val) { this.inCS = val; }
}