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
        resetPgApiCircuitBreaker();
    }

    @AfterEach
    void resetCircuitBreakerAfterEach() {
        resetPgApiCircuitBreaker();
    }

    private void resetPgApiCircuitBreaker() {
        if (circuitBreakerRegistry != null) {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-api");
            circuitBreaker.transitionToClosedState();
            circuitBreaker.reset();
        }
    }
}
