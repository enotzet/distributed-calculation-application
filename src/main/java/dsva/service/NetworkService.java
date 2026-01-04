package dsva.service;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

@Service
public class NetworkService {
    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private LogicalClockService clock;

    @Autowired
    private LometService lometService;

    @Autowired
    private TopologyService topologyService;

    @Setter
    private int delay = 1000;
    @Setter @Getter
    private boolean online = true;

    public void sendPost(String url, Object body) {
        if (!online) return;
        long timeToSend = clock.tick();

        new Thread(() -> {
            try {
                Thread.sleep(delay);
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Logical-Time", String.valueOf(timeToSend));
                HttpEntity<Object> entity = new HttpEntity<>(body, headers);
                rest.postForEntity(url, entity, String.class);
            } catch (Exception e) {
                clock.log("Failed to send message to " + url + ". Reason: " + e.getMessage());

                String neighborId = url.replace("http://", "").split("/api")[0];

                topologyService.removeNeighbor(neighborId);

                lometService.getGlobalWFG().removeIf(edge -> edge.getToId().equals(neighborId));
                lometService.removeAllWaitEdgesFrom(neighborId);
            }
        }).start();
    }
}