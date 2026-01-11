package dsva.controller;

import dsva.model.*;
import dsva.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

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
        checkOnline();
        topology.addNeighbor(bootstrapNode);

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Logical-Time", String.valueOf(clock.getTime()));

            NodeInfo me = new NodeInfo(topology.getMyId().split(":")[0], getMyPort());
            HttpEntity<NodeInfo> entity = new HttpEntity<>(me, headers);

            ResponseEntity<NodeInfo[]> response = restTemplate.postForEntity(
                    bootstrapNode.getBaseUrl() + "/api/register", entity, NodeInfo[].class);

            if (response.getBody() != null) {
                for (NodeInfo n : response.getBody()) {
                    topology.addNeighbor(n);
                }
            }
            return "Join successful. Neighbors: " + topology.getNeighbors().size();
        } catch (Exception e) {
            return "Join failed: " + e.getMessage();
        }
    }

    @PostMapping("/register")
    public List<NodeInfo> register(@RequestBody NodeInfo newNode) {
        checkOnline();
        if (!topology.getNeighbors().contains(newNode)) {
            for (NodeInfo neighbor : topology.getNeighbors()) {
                network.sendPost(neighbor.getBaseUrl() + "/api/register-proxy", newNode);
            }
            topology.addNeighbor(newNode);
        }
        return topology.getNeighbors();
    }

    @PostMapping("/register-proxy")
    public void registerProxy(@RequestBody NodeInfo newNode) {
        checkOnline();
        topology.addNeighbor(newNode);
    }

    @PostMapping("/leave")
    public void leave() {
        checkOnline();
        clock.log("Graceful leave...");
        String myId = topology.getMyId();

        for (NodeInfo n : topology.getNeighbors()) {
            network.sendPost(n.getBaseUrl() + "/api/unregister/" + myId, null);
        }

        lomet.executeInCS(() -> lomet.handleNodeFailure(myId));

        topology.getNeighbors().clear();
    }

    @PostMapping("/unregister/{id}")
    public void unregister(@PathVariable String id) {
        checkOnline();
        topology.removeNeighbor(id);
        lomet.executeInCS(() -> lomet.handleNodeFailure(id));
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


    private void checkOnline() {
        if (!network.isOnline()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Node is offline");
        }
    }

    private int getMyPort() {
        return Integer.parseInt(topology.getMyId().split(":")[1]);
    }

    @PostMapping("/resource/preliminary")
    public void resPreliminary(@RequestBody List<String> resources) {
        lomet.sendPreliminaryRequests(resources);
    }

    // ШАГ 2: Acquire (с апостериорной детекцией внутри)
    @GetMapping("/resource/acquire")
    public String resAcquire(@RequestParam String resourceId) {
        boolean success = lomet.acquireResource(resourceId);
        return success ? "SUCCESS" : "DEADLOCK_RELEASED";
    }

    @PostMapping("/resource/release")
    public void resRelease(@RequestParam String resourceId) {
        lomet.releaseResource(resourceId);
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
}