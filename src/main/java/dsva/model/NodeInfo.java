package dsva.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class NodeInfo {
    private String host;
    private int port;

    public String getId() {
        return host + ":" + port;
    }

    public String getBaseUrl() {
        return "http://" + host + ":" + port;
    }
}