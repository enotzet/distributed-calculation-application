package dsva.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DependencyEdge {
    private String fromId;
    private String toId;
    private long logicalTime;
}
