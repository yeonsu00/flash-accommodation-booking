package com.flashaccommodationbooking.support;

import org.redisson.api.RedissonClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

public abstract class IntegrationTest {

    @MockitoBean
    protected RedissonClient redissonClient;
}
