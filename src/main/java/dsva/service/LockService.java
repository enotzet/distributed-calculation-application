package dsva.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class LockService {
    @Autowired private TopologyService topology;
    @Autowired private LogicalClockService clock;

    private final AtomicBoolean isLocked = new AtomicBoolean(false);
    private String currentOwner = null;

    public synchronized boolean tryAcquire(String nodeId) {
        if (!isLocked.get() || nodeId.equals(currentOwner)) {
            isLocked.set(true);
            currentOwner = nodeId;
            return true;
        }
        return false;
    }

    public synchronized void release(String nodeId) {
        if (nodeId.equals(currentOwner)) {
            isLocked.set(false);
            currentOwner = null;
        }
    }
}
