package dsva.controller;

import dsva.model.*;
import dsva.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class NodeController {
    @Autowired private LogicalClockService clock;
    @Autowired private LometService lomet;
    @Autowired private NetworkService network;

    private final List<NodeInfo> neighbors = new ArrayList<>();

    @Value("${server.port}") private int port;

    @PostMapping("/join")
    public String join(@RequestBody NodeInfo node) {
        clock.tick();
        if (!neighbors.contains(node)) {
            neighbors.add(node);
            clock.log("Připojen nový uzel: " + node.getId());
        }
        return "OK";
    }

    @PostMapping("/work/request")
    public String receiveWork(@RequestHeader("X-Logical-Time") long remoteTime, @RequestParam String fromId) {
        clock.update(remoteTime);
        String myId = "localhost:" + port;
        clock.log("Přijata práce od " + fromId);


        DependencyEdge edge = new DependencyEdge(fromId, myId, clock.getTime());
        lomet.addEdges(List.of(edge));

        for (NodeInfo n : neighbors) {
            network.sendPost(n.getBaseUrl() + "/api/lomet/update", lomet.getGlobalWFG());
        }

        return "Work processing or blocked";
    }

    @PostMapping("/lomet/update")
    public void updateLomet(@RequestBody List<DependencyEdge> edges, @RequestHeader("X-Logical-Time") long remoteTime) {
        clock.update(remoteTime);
        lomet.addEdges(edges);
    }

    @DeleteMapping("/kill")
    public void kill() {
        clock.log("Uzel končí (kill).");
        System.exit(0);
    }
}