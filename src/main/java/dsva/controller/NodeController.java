// file: src/main/java/dsva/controller/NodeController.java
package dsva.controller;

import dsva.model.*;
import dsva.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api")
public class NodeController {
    @Autowired private LogicalClockService clock;
    @Autowired private TopologyService topology;
    @Autowired private LometService lomet;
    @Autowired private NetworkService network;

    @PostMapping("/join")
    public String join(@RequestBody NodeInfo bootstrapNode) {
        topology.addNeighbor(bootstrapNode);
        clock.tick();

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Logical-Time", String.valueOf(clock.getTime()));
            HttpEntity<NodeInfo> entity = new HttpEntity<>(new NodeInfo("localhost", getMyPort()), headers);

            ResponseEntity<NodeInfo[]> response = restTemplate.postForEntity(
                    bootstrapNode.getBaseUrl() + "/api/register", entity, NodeInfo[].class);

            if (response.getBody() != null) {
                for (NodeInfo n : response.getBody()) {
                    topology.addNeighbor(n);
                }
            }
            return "Join successful. Neighbors count: " + topology.getNeighbors().size();
        } catch (Exception e) {
            return "Join failed: " + e.getMessage();
        }
    }

    @PostMapping("/register")
    public List<NodeInfo> register(@RequestBody NodeInfo newNode, @RequestHeader("X-Logical-Time") long time) {
        clock.update(time);

        if (!topology.getNeighbors().contains(newNode)) {
            for (NodeInfo neighbor : topology.getNeighbors()) {
                network.sendPost(neighbor.getBaseUrl() + "/api/register-proxy", newNode);
            }
            topology.addNeighbor(newNode);
        }

        return topology.getNeighbors();
    }

    // Odhlášení ze systému (Graceful Leave)
    @PostMapping("/leave")
    public void leave() {
        clock.log("Disconnecting from system");
        String myId = topology.getMyId();
        for (NodeInfo n : topology.getNeighbors()) {
            network.sendPost(n.getBaseUrl() + "/api/unregister/" + myId, null);
        }
        topology.getNeighbors().clear();
    }

    @PostMapping("/register-proxy")
    public void registerProxy(@RequestBody NodeInfo newNode, @RequestHeader("X-Logical-Time") long time) {
        clock.update(time);
        topology.addNeighbor(newNode);
    }

    @PostMapping("/unregister/{id}")
    public void unregister(@PathVariable String id, @RequestHeader("X-Logical-Time") long remoteTime) {
        clock.update(remoteTime);
        topology.removeNeighbor(id);
    }

    @PostMapping("/lomet/update")
    public void updateLomet(@RequestBody List<DependencyEdge> edges,
            @RequestParam String senderId,
            @RequestHeader("X-Logical-Time") long remoteTime) {
        clock.update(remoteTime);
        lomet.addEdges(senderId, edges, remoteTime);
    }

    @DeleteMapping("/kill")
    public void kill() {
        clock.log("Instant end(kill command)");
        System.exit(0);
    }

    private int getMyPort() {
        return Integer.parseInt(topology.getMyId().split(":")[1]);
    }
}