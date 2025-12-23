package dsva.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

@Service
public class NetworkService {
    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private LogicalClockService clock;

    private final int DELAY_MS = 1000;

    public void sendPost(String url, Object body) {
        new Thread(() -> {
            try {
                Thread.sleep(DELAY_MS);
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Logical-Time", String.valueOf(clock.getTime()));
                HttpEntity<Object> entity = new HttpEntity<>(body, headers);
                rest.postForEntity(url, entity, String.class);
            } catch (Exception e) {
                clock.log("Chyba při odesílání na " + url);
            }
        }).start();
    }
}