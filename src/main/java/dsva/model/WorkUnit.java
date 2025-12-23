package dsva.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @AllArgsConstructor @NoArgsConstructor
public class WorkUnit {
    private String id;
    private int load;
    private String senderId;
}
