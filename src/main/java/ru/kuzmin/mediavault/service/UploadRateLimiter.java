package ru.kuzmin.mediavault.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.kuzmin.mediavault.exception.UploadRateLimitExceededException;

import java.util.List;
import java.util.UUID;

@Component
@Profile("media-processing")
public class UploadRateLimiter {
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            if current >= tonumber(ARGV[1]) then
                return redis.call('PTTL', KEYS[1])
            end
            if redis.call('INCR', KEYS[1]) == 1 then
                redis.call('PEXPIRE', KEYS[1], 60000)
            end
            return -1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final String limit;

    public UploadRateLimiter(StringRedisTemplate redis, @Value("${media.uploads-per-minute:10}") int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("media.uploads-per-minute must be positive");
        }
        this.redis = redis;
        this.limit = Integer.toString(limit);
    }

    public void acquire(UUID ownerId) {
        Long remainingMs;
        try {
            remainingMs = redis.execute(ACQUIRE, List.of("mediavault:upload-limit:" + ownerId), limit);
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Upload limiter unavailable", e);
        }
        if (remainingMs == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Upload limiter unavailable");
        }
        if (remainingMs >= 0) {
            throw new UploadRateLimitExceededException(Math.max(1, (remainingMs + 999) / 1000));
        }
    }
}
