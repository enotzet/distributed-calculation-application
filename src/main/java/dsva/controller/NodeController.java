// file: src/main/java/dsva/controller/NodeController.java
package dsva.controller;

import dsva.model.*;
import dsva.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api")
public class NodeController {
    @Autowired private LometService lomet;
    @Autowired private RicartAgrawalaService raService;
    @Autowired private TopologyService topology;
    @Autowired private LogicalClockService clock;
    @Autowired private NetworkService network;

    @PostMapping("/join")
    public String join(@RequestBody NodeInfo bootstrapNode) {
        topology.addNeighbor(bootstrapNode);
        try {
            RestTemplate restTemplate = new RestTemplate();
            NodeInfo me = new NodeInfo(topology.getMyId().split(":")[0], getMyPort());

            ResponseEntity<NodeInfo[]> response = restTemplate.postForEntity(
                    bootstrapNode.getBaseUrl() + "/api/register", me, NodeInfo[].class);

            if (response.getBody() != null) {
                for (NodeInfo n : response.getBody()) {
                    if (!n.getId().equals(topology.getMyId())) {
                        topology.addNeighbor(n);
                        network.sendPost(n.getBaseUrl() + "/api/register-proxy", me);
                    }
                }
            }
            return "Join successful. Neighbors: " + topology.getNeighbors().size();
        } catch (Exception e) {
            return "Join failed: " + e.getMessage();
        }
    }

    @PostMapping("/register-proxy")
    public void registerProxy(@RequestBody NodeInfo newNode) {
        if (!network.isOnline()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Node is offline");
        }
        topology.addNeighbor(newNode);
    }

    @PostMapping("/register")
    public List<NodeInfo> register(@RequestBody NodeInfo newNode) {
        if (!network.isOnline()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Node is offline");
        }

        topology.addNeighbor(newNode);

        List<NodeInfo> allKnownNodes = new ArrayList<>(topology.getNeighbors());
        allKnownNodes.add(new NodeInfo(topology.getMyId().split(":")[0], getMyPort()));

        return allKnownNodes;
    }

    @PostMapping("/resource/preliminary")
    public void resPreliminary(@RequestBody List<String> resources) {
        if (!network.isOnline()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Node is offline"
            );
        }

        lomet.sendPreliminaryRequests(resources);
    }

    @GetMapping("/resource/acquire")
    public String resAcquire(@RequestParam String resourceId) {
        if (!network.isOnline()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        boolean success = lomet.acquireResource(resourceId);
        // Возвращаем результат для скрипта
        return success ? "SUCCESS" : "DEADLOCK_RELEASED";
    }

    @PostMapping("/resource/release")
    public void resRelease(@RequestParam String resourceId) {
        if (!network.isOnline()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        lomet.releaseResource(resourceId);
    }


    @PostMapping("/unregister/{id}")
    public void unregister(@PathVariable String id) {
        if (!network.isOnline()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        topology.removeNeighbor(id);
        lomet.executeInCS(() -> lomet.handleNodeFailure(id));
    }

    @PostMapping("/leave")
    public void leave() {
        if (!network.isOnline()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        String myId = topology.getMyId();
        for (NodeInfo n : topology.getNeighbors()) {
            network.sendPost(n.getBaseUrl() + "/api/unregister/" + myId, null);
        }
        lomet.executeInCS(() -> lomet.handleNodeFailure(myId));
        topology.getNeighbors().clear();
    }

    @DeleteMapping("/kill")
    public void kill() {
        network.setOnline(false);
        clock.log("Node KILLED (Communication offline)");
    }

    @PostMapping("/revive")
    public void revive() {
        network.setOnline(true);
        clock.log("Node REVIVED");
    }

    @PostMapping("/ra/request")
    public void handleRaRequest(@RequestParam long time, @RequestParam String senderId) {
        raService.onReceiveRequest(time, senderId);
    }

    @PostMapping("/ra/reply")
    public void handleRaReply() {
        raService.onReceiveReply();
    }

    @PostMapping("/lomet/sync")
    public void syncLomet(@RequestBody Map<String, Object> state) {
        lomet.sync(state);
    }

    private int getMyPort() {
        return Integer.parseInt(topology.getMyId().split(":")[1]);
    }
}