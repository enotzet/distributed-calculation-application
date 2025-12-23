// file: src/main/java/dsva/controller/NodeController.java
package dsva.controller;

import dsva.model.*;
import dsva.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class NodeController {
    @Autowired private LogicalClockService clock;
    @Autowired private TopologyService topology;
    @Autowired private LometService lomet;
    @Autowired private NetworkService network;

    @PostMapping("/join")
    public String join(@RequestBody NodeInfo newNode) {
        clock.tick();
        // Přidáme souseda lokálně
        topology.addNeighbor(newNode);
        // Pošleme mu naše info, aby si nás přidal taky
        network.sendPost(newNode.getBaseUrl() + "/api/register",
                new NodeInfo("localhost", getMyPort()));
        return "Uzel připojen";
    }

    @PostMapping("/register")
    public void register(@RequestBody NodeInfo node, @RequestHeader("X-Logical-Time") long remoteTime) {
        clock.update(remoteTime);
        topology.addNeighbor(node);
    }

    // Odhlášení ze systému (Graceful Leave)
    @PostMapping("/leave")
    public void leave() {
        clock.log("Odpojuji se ze systému.");
        String myId = topology.getMyId();
        for (NodeInfo n : topology.getNeighbors()) {
            network.sendPost(n.getBaseUrl() + "/api/unregister/" + myId, null);
        }
        topology.getNeighbors().clear();
    }

    @PostMapping("/unregister/{id}")
    public void unregister(@PathVariable String id, @RequestHeader("X-Logical-Time") long remoteTime) {
        clock.update(remoteTime);
        topology.removeNeighbor(id);
    }

    @PostMapping("/lomet/update")
    public void updateLomet(@RequestBody List<DependencyEdge> edges, @RequestHeader("X-Logical-Time") long remoteTime) {
        clock.update(remoteTime);
        lomet.addEdges(edges);
    }

    @DeleteMapping("/kill")
    public void kill() {
        clock.log("Okamžité ukončení (bez odhlášení).");
        System.exit(0);
    }

    private int getMyPort() {
        return Integer.parseInt(topology.getMyId().split(":")[1]);
    }
}