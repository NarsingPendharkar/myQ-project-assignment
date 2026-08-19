package com.schwab.audit.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Small in-memory throttle to prevent repeated credential guessing per username. */
@Service
public class LoginAttemptService {
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration window;

    public LoginAttemptService(@Value("${app.security.login.max-attempts:5}") int maxAttempts,
                               @Value("${app.security.login.window-seconds:900}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
    }
    public boolean isBlocked(String username) {
        Attempt attempt = attempts.get(username);
        if (attempt == null || attempt.firstFailure.plus(window).isBefore(Instant.now())) { attempts.remove(username); return false; }
        return attempt.count >= maxAttempts;
    }
    public void recordFailure(String username) {
        attempts.compute(username, (key, current) -> current == null || current.firstFailure.plus(window).isBefore(Instant.now())
                ? new Attempt(1, Instant.now()) : new Attempt(current.count + 1, current.firstFailure));
    }
    public void clear(String username) { attempts.remove(username); }
    private record Attempt(int count, Instant firstFailure) { }
}
