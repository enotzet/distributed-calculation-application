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
    @Autowired private LockService lockService;
    @Autowired private NetworkService network;

    @PostMapping("/join")
    public String join(@RequestBody NodeInfo bootstrapNode) {
        if (!network.isOnline()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Node is dead");
        }
        topology.addNeighbor(bootstrapNode);
        clock.tick();

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Logical-Time", String.valueOf(clock.getTime()));
            HttpEntity<NodeInfo> entity = new HttpEntity<>(new NodeInfo(topology.getMyId().split(":")[0], getMyPort()), headers);

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

    @PostMapping("/lomet/startDetection")
    public void startDetection() {
        lomet.checkDeadlock();
    }

    @PostMapping("/register")
    public List<NodeInfo> register(@RequestBody NodeInfo newNode, @RequestHeader("X-Logical-Time") long time) {
        if (!network.isOnline()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Node is dead");
        }
        clock.update(time);

        if (!topology.getNeighbors().contains(newNode)) {
            for (NodeInfo neighbor : topology.getNeighbors()) {
                network.sendPost(neighbor.getBaseUrl() + "/api/register-proxy", newNode);
            }
            topology.addNeighbor(newNode);
        }

        return topology.getNeighbors();
    }

    @GetMapping("/lock/acquire")
    public boolean acquireLock(@RequestParam String nodeId) {
        if (!network.isOnline()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Node is dead");
        }
        return lockService.tryAcquire(nodeId);
    }

    @GetMapping("/lock/release")
    public void releaseLock(@RequestParam String nodeId) {
        lockService.release(nodeId);
    }

    @PostMapping("/lomet/sync")
    public void syncLomet(@RequestBody List<DependencyEdge> edges, @RequestHeader("X-Logical-Time") long time) {
        clock.update(time);
        lomet.syncEdges(edges);
    }

    @PostMapping("/leave")
    public void leave() {
        if (!network.isOnline()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Node is dead");
        }

        clock.log("Disconnecting from system");
        String myId = topology.getMyId();

        lomet.executeInCS(() -> {
            lomet.removeEdgesInvolving(myId);
        });

        for (NodeInfo n : topology.getNeighbors()) {
            network.sendPost(n.getBaseUrl() + "/api/unregister/" + myId, null);
        }
        topology.getNeighbors().clear();
    }

    @PostMapping("/register-proxy")
    public void registerProxy(@RequestBody NodeInfo newNode, @RequestHeader("X-Logical-Time") long time) {
        if (!network.isOnline()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Node is dead");
        }

        clock.update(time);
        topology.addNeighbor(newNode);
    }

    @PostMapping("/unregister/{id}")
    public void unregister(@PathVariable String id, @RequestHeader("X-Logical-Time") long remoteTime) {
        if (!network.isOnline()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Node is dead");
        }

        clock.update(remoteTime);
        topology.removeNeighbor(id);
        lomet.removeEdgesInvolving(id);
    }

    @PostMapping("/setDelay")
    public void setDelay(@RequestParam int value) {
        if (!network.isOnline()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Node is dead");
        }

        network.setDelay(value);
    }

    @PostMapping("/revive")
    public void revive() {
        network.setOnline(true);
        clock.log("Node revived");
        List<NodeInfo> oldNeighbors = topology.getNeighbors();
        String myIp = topology.getMyId().split(":")[0];
        NodeInfo me = new NodeInfo( myIp, getMyPort() );

        for (NodeInfo neighbor : oldNeighbors) {
            network.sendPost(neighbor.getBaseUrl() + "/api/register-proxy", me);
        }
    }

    @DeleteMapping("/kill")
    public void kill() {
        network.setOnline(false);
        clock.log("Instant end(kill command)");
    }

    private int getMyPort() {
        return Integer.parseInt(topology.getMyId().split(":")[1]);
    }
}