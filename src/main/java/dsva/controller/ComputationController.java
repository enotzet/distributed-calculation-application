package dsva.controller;

import dsva.model.WorkUnit;
import dsva.service.ComputationService;
import dsva.service.LogicalClockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/work")
public class ComputationController {
    @Autowired
    private ComputationService computeService;

    @Autowired
    private LogicalClockService logger;

    // Spuštění výpočtu (z CLI nebo skriptu)
    @PostMapping("/start")
    public String start(@RequestParam int amount) {
        computeService.initiateWork(amount);
        return "Calculation started";
    }

    @PostMapping("/receive")
    public void receive(@RequestBody WorkUnit unit, @RequestHeader("X-Logical-Time") long time) {
        logger.update(time);
        computeService.receiveWork(unit);
    }

    @PostMapping("/request-grant")
    public void grantWork(@RequestBody String requesterId, @RequestHeader("X-Logical-Time") long time) {
        logger.update(time);
        logger.log("Node " + requesterId + " asking me for job");
        computeService.passWork();
    }

    @PostMapping("/pass")
    public void pass() {
        computeService.passWork();
    }
}