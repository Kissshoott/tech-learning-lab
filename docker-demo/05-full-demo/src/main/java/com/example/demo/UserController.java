package com.example.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    @GetMapping
    public List<User> list() {
        return userRepository.findAll();
    }

    @PostMapping
    public User create(@RequestBody User user) {
        User saved = userRepository.save(user);
        // 写入 Redis 缓存，演示应用同时连接 MySQL 和 Redis
        redisTemplate.opsForValue().set(
            "user:" + saved.getId(),
            saved.getName(),
            Duration.ofMinutes(30)
        );
        return saved;
    }

    @GetMapping("/cache/{id}")
    public String getCached(@PathVariable Long id) {
        String cached = redisTemplate.opsForValue().get("user:" + id);
        return cached != null ? cached : "not cached";
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
