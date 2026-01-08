package dsva.service;

import dsva.model.NodeInfo;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

@Service
public class NetworkService {
    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private LogicalClockService clock;

    @Autowired
    private TopologyService topologyService;

    @Autowired
    @Lazy
    private LometService lomet;

    @Setter
    private int delay = 1000;
    @Setter @Getter
    private boolean online = true;

    public void sendPost(String url, Object body) {
        if (!online) return;
        long time = clock.tick();
        new Thread(() -> {
            try {
                Thread.sleep(delay);
                if (!online) return;
                HttpHeaders h = new HttpHeaders();
                h.set("X-Logical-Time", String.valueOf(time));
                rest.postForEntity(url, new HttpEntity<>(body, h), String.class);
            } catch (Exception e) {
                clock.log("Comm failure with " + url + ". Removing neighbor.");
                String neighborId = url.replace("http://", "").split("/api")[0];
                topologyService.removeNeighbor(neighborId);
                lomet.removeEdgesInvolving(neighborId);
                broadcastDeath(neighborId);
            }
        }).start();
    }

    public boolean requestLockFromLeader(String leaderId) {
        try {
            String url = "http://" + leaderId + "/api/lock/acquire?nodeId=" + clock.getPort();
            return Boolean.TRUE.equals(rest.getForObject(url, Boolean.class));
        } catch (Exception e) {
            clock.log("Leader " + leaderId + " is not responding. Removing him.");
            topologyService.removeNeighbor(leaderId);
            return false;
        }
    }

    public void releaseLockOnLeader(String leaderId) {
        try {
            rest.getForObject("http://" + leaderId + "/api/lock/release?nodeId=" + clock.getPort(), String.class);
        } catch (Exception e) { }
    }

    private void broadcastDeath(String deadNodeId) {
        for ( NodeInfo n : topologyService.getNeighbors()) {
            sendPost(n.getBaseUrl() + "/api/unregister/" + deadNodeId, null);
        }
    }
}