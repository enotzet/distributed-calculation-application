package dsva.service;

import dsva.model.NodeInfo;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class NetworkService {
    private final RestTemplate rest = new RestTemplate();
    @Autowired private TopologyService topologyService;

    @Getter
    @Setter
    private boolean online = true;

    public void sendPost(String url, Object body) {
        if (!online) return;
        new Thread(() -> {
            try { rest.postForEntity(url, body, String.class); } catch (Exception e) {}
        }).start();
    }

    public void sendRaRequest(String baseUrl, long time, String myId) {
        if (!online) return;
        String url = baseUrl + "/api/ra/request?time=" + time + "&senderId=" + myId;
        new Thread(() -> {
            try { rest.postForEntity(url, null, String.class); } catch (Exception e) {}
        }).start();
    }

    public void sendRaReply(String senderId) {
        if (!online) return;
        NodeInfo n = topologyService.getNeighborById(senderId);
        if (n != null) {
            new Thread(() -> {
                try { rest.postForEntity(n.getBaseUrl() + "/api/ra/reply", null, String.class); } catch (Exception e) {}
            }).start();
        }
    }
}