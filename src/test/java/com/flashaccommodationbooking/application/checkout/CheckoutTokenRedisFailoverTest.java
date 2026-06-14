package com.flashaccommodationbooking.application.checkout;

import com.flashaccommodationbooking.domain.checkout.CheckoutToken;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("checkoutToken Redis 장애 DB 폴백 테스트")
class CheckoutTokenRedisFailoverTest extends IntegrationTest {

    private static final Long USER_ID = 1L;
    private static final String CHECKOUT_TOKEN = "checkout-token-failover";

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private CheckoutRepository checkoutRepository;

    @Autowired
    private CheckoutTokenJpaRepository checkoutTokenJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

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

        userJpaRepository.save(User.of("token-user", 100_000));
        product = productJpaRepository.save(BookingTestFixtures.defaultProduct());
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAll();
        redisCleanUp.truncateAll();
    }

    @DisplayName("Redis 정상 시 Redis에서 tokenInfo를 조회한다")
    @Test
    void returnsTokenInfo_fromRedis_whenRedisIsHealthy() {
        // arrange
        redisTemplate.opsForValue().set("checkout:" + CHECKOUT_TOKEN, USER_ID + ":" + product.getId());

        // act
        CheckoutInfo.TokenInfo tokenInfo = checkoutService.getCheckoutTokenInfo(CHECKOUT_TOKEN);

        // assert
        assertThat(tokenInfo.userId()).isEqualTo(USER_ID);
        assertThat(tokenInfo.productId()).isEqualTo(product.getId());
    }

    @DisplayName("Redis 장애 시 checkout_token 테이블에서 tokenInfo를 조회한다")
    @Test
    void returnsTokenInfo_fromDb_whenRedisFails() {
        // arrange
        checkoutTokenJpaRepository.save(CheckoutToken.of(CHECKOUT_TOKEN, USER_ID, product.getId()));
        doReturn(Optional.empty()).when(checkoutRedisRepository).getCheckoutTokenValue(eq(CHECKOUT_TOKEN));

        // act
        CheckoutInfo.TokenInfo tokenInfo = checkoutService.getCheckoutTokenInfo(CHECKOUT_TOKEN);

        // assert
        assertThat(tokenInfo.userId()).isEqualTo(USER_ID);
        assertThat(tokenInfo.productId()).isEqualTo(product.getId());
    }

    @DisplayName("Redis 장애이고 DB에도 토큰이 없으면 CHECKOUT_TOKEN_NOT_FOUND(404)를 던진다")
    @Test
    void throwsTokenNotFound_whenBothRedisAndDbHaveNoToken() {
        // arrange
        doReturn(Optional.empty()).when(checkoutRedisRepository).getCheckoutTokenValue(anyString());

        // act & assert
        BusinessException exception = assertThrows(BusinessException.class, () ->
                checkoutService.getCheckoutTokenInfo(CHECKOUT_TOKEN)
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHECKOUT_TOKEN_NOT_FOUND);
    }

    @DisplayName("DB 토큰 expiredAt이 초과되면 CHECKOUT_TOKEN_NOT_FOUND(404)를 던진다")
    @Test
    void throwsTokenNotFound_whenTokenExpiredInDb() {
        // arrange
        CheckoutToken expiredToken = CheckoutToken.of(CHECKOUT_TOKEN, USER_ID, product.getId());
        ReflectionTestUtils.setField(expiredToken, "expiredAt", LocalDateTime.now().minusSeconds(1));
        checkoutTokenJpaRepository.save(expiredToken);
        doReturn(Optional.empty()).when(checkoutRedisRepository).getCheckoutTokenValue(eq(CHECKOUT_TOKEN));

        // act & assert
        BusinessException exception = assertThrows(BusinessException.class, () ->
                checkoutService.getCheckoutTokenInfo(CHECKOUT_TOKEN)
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHECKOUT_TOKEN_NOT_FOUND);
    }

    @DisplayName("재고 선점 성공 시 Redis와 DB에 토큰이 이중 저장된다")
    @Test
    void savesTokenToBothRedisAndDb_whenReserveStockSucceeds() {
        // arrange
        redisTemplate.opsForValue().set("stock:" + product.getId(), "10");

        // act
        checkoutRepository.reserveStock(product.getId(), USER_ID, CHECKOUT_TOKEN);

        // assert
        assertThat(redisTemplate.opsForValue().get("checkout:" + CHECKOUT_TOKEN))
                .isEqualTo(USER_ID + ":" + product.getId());
        assertThat(checkoutTokenJpaRepository.findById(CHECKOUT_TOKEN)).isPresent();
    }
}
