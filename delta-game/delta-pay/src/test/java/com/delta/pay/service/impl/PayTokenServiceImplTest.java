package com.delta.pay.service.impl;

import com.delta.common.exception.BusinessException;
import com.delta.pay.domain.PayTokenPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayTokenServiceImplTest {

    @Mock
    StringRedisTemplate redis;
    @Mock
    ValueOperations<String, String> valueOps;

    PayTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new PayTokenServiceImpl(redis, "test-secret-key-32bytes!!!!!!", 900);
    }

    @Test
    void issue_then_verify_roundTrip() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        String token = service.issue(1001L, 2002L);
        assertTrue(token.split("\\.").length == 5);
        assertTrue(token.length() < 120);
        PayTokenPayload p = service.verify(token);
        assertEquals(1001L, p.orderId());
        assertEquals(2002L, p.userId());
    }

    @Test
    void verify_tamperedSig_throws() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        String token = service.issue(1L, 2L);
        String bad = token.substring(0, token.length() - 2) + "aa";
        assertThrows(BusinessException.class, () -> service.verify(bad));
    }

    @Test
    void verify_expired_throws() {
        String token = service.buildTokenForTest(1L, 2L, Instant.now().getEpochSecond() - 10, "abcdefgh");
        assertThrows(BusinessException.class, () -> service.verify(token));
    }
}
