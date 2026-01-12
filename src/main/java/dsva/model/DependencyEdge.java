package dsva.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DependencyEdge {
    @EqualsAndHashCode.Include
    private String fromId;
    @EqualsAndHashCode.Include
    private String toId;

    private String resourceId;

    private long logicalTime;
}