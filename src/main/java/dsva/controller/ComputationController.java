package dsva.controller;

import dsva.model.WorkUnit;
import dsva.service.ComputationService;
import dsva.service.LogicalClockService;
import dsva.service.LometService;
import dsva.service.NetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/work")
public class ComputationController {
    @Autowired
    private ComputationService computeService;

    @Autowired
    private LogicalClockService logger;

    @Autowired
    private NetworkService networkService;

    @PostMapping("/start")
    public String start(@RequestParam int amount) {
        computeService.initiateWork(amount);
        return "Calculation started";
    }

    @PostMapping("/waitForMessage")
    public void waitForMessage(@RequestBody String targetId) {
        computeService.requestWorkFromNeighbor(targetId.trim());
    }

    @PostMapping("/setActive")
    public void setActive() {
        computeService.setActive();
    }

    @PostMapping("/setPassive")
    public void setPassive() {
        computeService.setPassive();
    }

    @PostMapping("/request")
    public void requestResource(@RequestBody String targetId) {
        computeService.requestWorkFromNeighbor(targetId);
    }

    @PostMapping("/receiveMessage")
    public void receiveMessage(@RequestBody WorkUnit unit, @RequestHeader("X-Logical-Time") long time) {
        if ( !networkService.isOnline() )
            throw new ResponseStatusException( HttpStatus.SERVICE_UNAVAILABLE );
        logger.update(time);
        computeService.receiveWork(unit);
    }

    @PostMapping("/request-grant")
    public void grantWork(@RequestBody String requesterId, @RequestHeader("X-Logical-Time") long time) {
        if ( !networkService.isOnline() )
            throw new ResponseStatusException( HttpStatus.SERVICE_UNAVAILABLE );        logger.update(time);
        String cleanId = requesterId.trim().replace("\"", "");
        logger.log("Node " + cleanId + " asking me for job. Granting...");

        // Передаем работу именно тому, кто попросил
        computeService.passWork(cleanId);
    }

    @PostMapping("/pass")
    public void pass() {
        computeService.passWork(null);
    }
}