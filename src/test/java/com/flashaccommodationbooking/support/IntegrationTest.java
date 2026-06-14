package com.flashaccommodationbooking.support;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

public abstract class IntegrationTest {

    @MockitoBean
    protected RedissonClient redissonClient;

    @Autowired(required = false)
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreakerBeforeEach() {
        resetCircuitBreaker("pg-api");
        resetCircuitBreaker("redis");
    }

    @AfterEach
    void resetCircuitBreakerAfterEach() {
        resetCircuitBreaker("pg-api");
        resetCircuitBreaker("redis");
    }

    private void resetCircuitBreaker(String name) {
        if (circuitBreakerRegistry != null) {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
            circuitBreaker.transitionToClosedState();
            circuitBreaker.reset();
        }
    }
}
