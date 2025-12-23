package dsva.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LogicalClockService {
    private final AtomicLong clock = new AtomicLong(0);

    @Value("${server.port}")
    private int port;

    public long tick() {
        return clock.incrementAndGet();
    }

    public void update(long remoteTime) {
        clock.set(Math.max(clock.get(), remoteTime) + 1);
    }

    public long getTime() {
        return clock.get();
    }

    public synchronized void log(String message) {
        String logEntry = String.format("[%d] [Node:%d] %s", clock.get(), port, message);
        System.out.println(logEntry);
        try (PrintWriter out = new PrintWriter(new FileWriter("node_" + port + ".log", true))) {
            out.println(LocalDateTime.now() + " " + logEntry);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}