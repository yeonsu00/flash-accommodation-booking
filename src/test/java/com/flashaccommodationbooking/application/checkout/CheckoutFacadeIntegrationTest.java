package com.flashaccommodationbooking.application.checkout;

import com.flashaccommodationbooking.application.queue.QueueInfo;
import com.flashaccommodationbooking.application.queue.QueueService;
import com.flashaccommodationbooking.domain.queue.QueueStatus;
import com.flashaccommodationbooking.application.product.ProductService;
import com.flashaccommodationbooking.application.user.UserService;
import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.domain.user.User;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import com.flashaccommodationbooking.infrastructure.product.ProductJpaRepository;
import com.flashaccommodationbooking.infrastructure.user.UserJpaRepository;
import com.flashaccommodationbooking.support.IntegrationTest;
import com.flashaccommodationbooking.support.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;
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
    private static final String CHECKOUT_TOKEN = "checkout-token-uuid";
    private static final long RECEIVED_AT = 1_000L;
    private static final LocalDateTime CHECK_IN = LocalDateTime.of(2026, 7, 1, 15, 0);
    private static final LocalDateTime CHECK_OUT = LocalDateTime.of(2026, 7, 2, 11, 0);

    @Autowired
    private CheckoutFacade checkoutFacade;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @MockitoSpyBean
    private QueueService queueService;

    @MockitoSpyBean
    private CheckoutService checkoutService;

    @MockitoSpyBean
    private ProductService productService;

    @MockitoSpyBean
    private UserService userService;

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
        userJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        userJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
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

    @DisplayName("getOrderSheet()")
    @Nested
    class GetOrderSheet {

        @DisplayName("유효한 checkoutToken이면, OrderSheet를 반환하고 관련 서비스를 각 1회 호출한다.")
        @Test
        void returnsOrderSheet_whenCheckoutTokenIsValid() {
            // arrange
            User user = userJpaRepository.save(User.of("test-user", 150_000));
            AccommodationProduct product = productJpaRepository.save(
                    AccommodationProduct.of("강남 호텔", 200_000, CHECK_IN, CHECK_OUT, LocalDateTime.now().minusHours(1), 10)
            );
            saveCheckoutToken(CHECKOUT_TOKEN, user.getId(), product.getId());

            // act
            CheckoutInfo.OrderSheet orderSheet = checkoutFacade.getOrderSheet(CHECKOUT_TOKEN);

            // assert
            assertThat(orderSheet.checkoutToken()).isEqualTo(CHECKOUT_TOKEN);
            assertThat(orderSheet.productId()).isEqualTo(product.getId());
            assertThat(orderSheet.productName()).isEqualTo("강남 호텔");
            assertThat(orderSheet.price()).isEqualTo(200_000);
            assertThat(orderSheet.point()).isEqualTo(150_000);
            verify(checkoutService, times(1)).getCheckoutTokenInfo(CHECKOUT_TOKEN);
            verify(productService, times(1)).getProduct(product.getId());
            verify(userService, times(1)).getUser(user.getId());
        }

        @DisplayName("존재하지 않는 checkoutToken이면, CHECKOUT_TOKEN_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenCheckoutTokenNotFound() {
            // act & assert
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                checkoutFacade.getOrderSheet(CHECKOUT_TOKEN);
            });

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHECKOUT_TOKEN_NOT_FOUND);
            verify(productService, never()).getProduct(anyLong());
            verify(userService, never()).getUser(anyLong());
        }

        @DisplayName("상품이 DB에 없으면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductNotFound() {
            // arrange
            User user = userJpaRepository.save(User.of("test-user", 150_000));
            Long missingProductId = 9_999L;
            saveCheckoutToken(CHECKOUT_TOKEN, user.getId(), missingProductId);

            // act & assert
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                checkoutFacade.getOrderSheet(CHECKOUT_TOKEN);
            });

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
            verify(checkoutService, times(1)).getCheckoutTokenInfo(CHECKOUT_TOKEN);
            verify(productService, times(1)).getProduct(missingProductId);
            verify(userService, never()).getUser(anyLong());
        }

        @DisplayName("사용자가 DB에 없으면, USER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenUserNotFound() {
            // arrange
            AccommodationProduct product = productJpaRepository.save(
                    AccommodationProduct.of("강남 호텔", 200_000, CHECK_IN, CHECK_OUT, LocalDateTime.now().minusHours(1), 10)
            );
            Long missingUserId = 9_999L;
            saveCheckoutToken(CHECKOUT_TOKEN, missingUserId, product.getId());

            // act & assert
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                checkoutFacade.getOrderSheet(CHECKOUT_TOKEN);
            });

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
            verify(checkoutService, times(1)).getCheckoutTokenInfo(CHECKOUT_TOKEN);
            verify(productService, times(1)).getProduct(product.getId());
            verify(userService, times(1)).getUser(missingUserId);
        }
    }

    private void saveCheckoutToken(String checkoutToken, Long userId, Long productId) {
        redisTemplate.opsForValue().set("checkout:" + checkoutToken, userId + ":" + productId);
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
