package com.flashaccommodationbooking.application.checkout;

import com.flashaccommodationbooking.application.queue.QueueInfo;
import com.flashaccommodationbooking.application.queue.QueueService;
import com.flashaccommodationbooking.domain.queue.QueueStatus;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import com.flashaccommodationbooking.support.IntegrationTest;
import com.flashaccommodationbooking.support.utils.RedisCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class CheckoutFacadeIntegrationTest extends IntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;
    private static final String QUEUE_TOKEN = "queue-token-uuid";
    private static final long RECEIVED_AT = 1_000L;

    @Autowired
    private CheckoutFacade checkoutFacade;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @MockitoSpyBean
    private QueueService queueService;

    @MockitoSpyBean
    private CheckoutService checkoutService;

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("reserveStock()")
    @Nested
    class ReserveStock {

        @DisplayName("admitted 토큰이면, checkoutToken을 반환하고 재고 선점 및 admitted 제거를 호출한다.")
        @Test
        void returnsCheckoutToken_whenAdmittedTokenAndStockAvailable() {
            // arrange
            QueueInfo.TokenInfo tokenInfo = new QueueInfo.TokenInfo(USER_ID, PRODUCT_ID, QueueStatus.ADMITTED, RECEIVED_AT);
            doReturn(tokenInfo).when(queueService).getTokenInfo(QUEUE_TOKEN);
            doReturn(true).when(queueService).isAdmitted(PRODUCT_ID, QUEUE_TOKEN);
            doNothing().when(checkoutService).reserveStock(eq(PRODUCT_ID), eq(USER_ID), anyString());

            // act
            String checkoutToken = checkoutFacade.reserveStock(QUEUE_TOKEN);

            // assert
            assertThat(checkoutToken).isNotBlank();
            verify(checkoutService, times(1)).reserveStock(eq(PRODUCT_ID), eq(USER_ID), anyString());
            verify(queueService, times(1)).removeFromAdmitted(PRODUCT_ID, QUEUE_TOKEN);
        }

        @DisplayName("존재하지 않는 queueToken이면, QUEUE_TOKEN_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenQueueTokenNotFound() {
            // arrange
            String invalidToken = UUID.randomUUID().toString();

            // act & assert
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                checkoutFacade.reserveStock(invalidToken);
            });

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.QUEUE_TOKEN_NOT_FOUND);
            verify(checkoutService, never()).reserveStock(anyLong(), anyLong(), anyString());
            verify(queueService, never()).removeFromAdmitted(anyLong(), anyString());
        }

        @DisplayName("admitted Set에 없으면, CHECKOUT_NOT_ADMITTED 예외가 발생하고 재고 선점을 호출하지 않는다.")
        @Test
        void throwsException_whenNotAdmitted() {
            // arrange
            String queueToken = createWaitingQueueToken(USER_ID, PRODUCT_ID);

            // act & assert
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                checkoutFacade.reserveStock(queueToken);
            });

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHECKOUT_NOT_ADMITTED);
            verify(checkoutService, never()).reserveStock(anyLong(), anyLong(), anyString());
            verify(queueService, never()).removeFromAdmitted(anyLong(), anyString());
        }

        @DisplayName("재고가 소진되면, PRODUCT_OUT_OF_STOCK 예외가 발생하고 admitted 제거를 호출하지 않는다.")
        @Test
        void throwsException_whenOutOfStock() {
            // arrange
            initStock(PRODUCT_ID, 0);
            String queueToken = createAdmittedQueueToken(USER_ID, PRODUCT_ID);

            // act & assert
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                checkoutFacade.reserveStock(queueToken);
            });

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_OUT_OF_STOCK);
            verify(checkoutService, times(1)).reserveStock(eq(PRODUCT_ID), eq(USER_ID), anyString());
            verify(queueService, never()).removeFromAdmitted(PRODUCT_ID, queueToken);
        }
    }

    private void initStock(Long productId, int stock) {
        redisTemplate.opsForValue().set("stock:" + productId, String.valueOf(stock));
    }

    private String createAdmittedQueueToken(Long userId, Long productId) {
        String queueToken = UUID.randomUUID().toString();
        saveQueueTokenInfo(queueToken, userId, productId, QueueStatus.ADMITTED);
        redisTemplate.opsForSet().add("queue:admitted:" + productId, queueToken);
        return queueToken;
    }

    private String createWaitingQueueToken(Long userId, Long productId) {
        String queueToken = UUID.randomUUID().toString();
        saveQueueTokenInfo(queueToken, userId, productId, QueueStatus.WAITING);
        return queueToken;
    }

    private void saveQueueTokenInfo(String queueToken, Long userId, Long productId, QueueStatus status) {
        Map<String, String> fields = new HashMap<>();
        fields.put("userId", String.valueOf(userId));
        fields.put("productId", String.valueOf(productId));
        fields.put("status", status.name());
        fields.put("createdAt", String.valueOf(System.currentTimeMillis()));
        redisTemplate.opsForHash().putAll("queue:token:" + queueToken, fields);
    }
}
