package com.flashaccommodationbooking.application.checkout;

import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.domain.user.User;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import com.flashaccommodationbooking.infrastructure.checkout.CheckoutRedisRepository;
import com.flashaccommodationbooking.infrastructure.checkout.CheckoutTokenJpaRepository;
import com.flashaccommodationbooking.infrastructure.product.ProductJpaRepository;
import com.flashaccommodationbooking.infrastructure.user.UserJpaRepository;
import com.flashaccommodationbooking.support.BookingTestFixtures;
import com.flashaccommodationbooking.support.IntegrationTest;
import com.flashaccommodationbooking.support.utils.DatabaseCleanUp;
import com.flashaccommodationbooking.support.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("재고 선점 Redis 장애 DB 폴백 테스트")
class StockReservationRedisFailoverTest extends IntegrationTest {

    private static final Long USER_ID = 1L;
    private static final String CHECKOUT_TOKEN = "checkout-token-stock-failover";

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private CheckoutRepository checkoutRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private CheckoutTokenJpaRepository checkoutTokenJpaRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @MockitoSpyBean
    private CheckoutRedisRepository checkoutRedisRepository;

    private AccommodationProduct product;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAll();
        redisCleanUp.truncateAll();
        reset(checkoutRedisRepository);

        userJpaRepository.save(User.of("stock-user", 100_000));
        product = productJpaRepository.save(BookingTestFixtures.defaultProduct());
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAll();
        redisCleanUp.truncateAll();
    }

    @DisplayName("Redis 정상 시 Lua 스크립트로 재고를 선점하고 DB stock은 변경되지 않는다")
    @Test
    void reservesStock_viaLua_whenRedisIsHealthy() {
        // arrange
        redisTemplate.opsForValue().set("stock:" + product.getId(), "10");
        int dbStockBefore = product.getStock();

        // act
        checkoutService.reserveStock(product.getId(), USER_ID, CHECKOUT_TOKEN);

        // assert
        AccommodationProduct unchangedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
        assertThat(unchangedProduct.getStock()).isEqualTo(dbStockBefore);
        assertThat(redisTemplate.opsForValue().get("stock:" + product.getId())).isEqualTo("9");
        assertThat(redisTemplate.opsForValue().get("checkout:" + CHECKOUT_TOKEN))
                .isEqualTo(USER_ID + ":" + product.getId());
        verify(checkoutRedisRepository).reserveStock(eq(product.getId()), eq(USER_ID), eq(CHECKOUT_TOKEN));
    }

    @DisplayName("Redis 장애(-2L) 시 DB FOR UPDATE로 재고를 차감한다")
    @Test
    void reservesStock_viaDbLock_whenRedisFails() {
        // arrange
        doReturn(-2L).when(checkoutRedisRepository)
                .reserveStock(eq(product.getId()), eq(USER_ID), eq(CHECKOUT_TOKEN));

        // act
        long result = checkoutRepository.reserveStock(product.getId(), USER_ID, CHECKOUT_TOKEN);

        // assert
        assertThat(result).isEqualTo(1L);
        AccommodationProduct updatedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(9);
    }

    @DisplayName("DB fallback 중 stock이 0이면 PRODUCT_OUT_OF_STOCK(409)을 던진다")
    @Test
    void throwsOutOfStock_whenDbStockIsZero_onRedisFallback() {
        // arrange
        AccommodationProduct soldOutProduct = productJpaRepository.save(
                AccommodationProduct.of(
                        "품절 상품",
                        BookingTestFixtures.PRODUCT_PRICE,
                        BookingTestFixtures.CHECK_IN,
                        BookingTestFixtures.CHECK_OUT,
                        java.time.LocalDateTime.now().minusHours(1),
                        0
                )
        );
        doReturn(-2L).when(checkoutRedisRepository)
                .reserveStock(eq(soldOutProduct.getId()), eq(USER_ID), eq(CHECKOUT_TOKEN));

        // act & assert
        BusinessException exception = assertThrows(BusinessException.class, () ->
                checkoutService.reserveStock(soldOutProduct.getId(), USER_ID, CHECKOUT_TOKEN)
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_OUT_OF_STOCK);
    }

    @DisplayName("DB fallback 성공 시 checkout_token 테이블에 토큰이 저장된다")
    @Test
    void savesCheckoutTokenToDb_whenStockReservedViaDbFallback() {
        // arrange
        doReturn(-2L).when(checkoutRedisRepository)
                .reserveStock(eq(product.getId()), eq(USER_ID), eq(CHECKOUT_TOKEN));

        // act
        checkoutRepository.reserveStock(product.getId(), USER_ID, CHECKOUT_TOKEN);

        // assert
        assertThat(checkoutTokenJpaRepository.findById(CHECKOUT_TOKEN)).isPresent();
        assertThat(checkoutTokenJpaRepository.findById(CHECKOUT_TOKEN).orElseThrow().getUserId()).isEqualTo(USER_ID);
        assertThat(checkoutTokenJpaRepository.findById(CHECKOUT_TOKEN).orElseThrow().getProductId())
                .isEqualTo(product.getId());
    }
}
