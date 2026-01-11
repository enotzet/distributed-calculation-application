package dsva.service;

import dsva.model.NodeInfo;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class NetworkService {
    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private TopologyService topologyService;

    @Autowired
    private LogicalClockService logger;

    @Autowired @Lazy
    private LometService lomet;

    @Getter @Setter private boolean online = true;
    @Getter @Setter private int delay = 0;

    public void sendPost(String url, Object body) {
        if (!online) return;
        new Thread(() -> {
            try {
                if (delay > 0) Thread.sleep(delay);
                rest.postForEntity(url, body, String.class);
            } catch (Exception e) {}
        }).start();
    }

    public void sendRaRequest(String baseUrl, long time, String myId) {
        if (!online) return;
        String url = baseUrl + "/api/ra/request?time=" + time + "&senderId=" + myId;
        new Thread(() -> {
            try {
                if (delay > 0) Thread.sleep(delay);
                rest.postForEntity(url, null, String.class);
            } catch (Exception e) {}
        }).start();
    }

    public void sendRaReply(String senderId) {
        if (!online) return;
        NodeInfo n = topologyService.getNeighborById(senderId);
        if (n != null) {
            new Thread(() -> {
                try {
                    if (delay > 0) Thread.sleep(delay);
                    rest.postForEntity(n.getBaseUrl() + "/api/ra/reply", null, String.class);
                } catch (Exception e) {}
            }).start();
        }
    }

    public void reportFailure(String nodeId) {
        logger.log("!!! FAILURE DETECTED !!! Node " + nodeId + " is not responding.");

        topologyService.removeNeighbor(nodeId);

        lomet.executeInCS(() -> {
            lomet.handleNodeFailure(nodeId);
        });
    }

    @Scheduled(fixedDelay = 1000)
    public void heartbeat() {
        if (!online) return;

        for (NodeInfo n : topologyService.getNeighbors()) {
            try {
                rest.getForObject(n.getBaseUrl() + "/api/ping", String.class);
            } catch (Exception e) {
                reportFailure(n.getId());
            }
        }
    }
}