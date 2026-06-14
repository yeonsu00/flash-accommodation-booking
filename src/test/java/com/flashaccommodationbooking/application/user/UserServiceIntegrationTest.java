package com.flashaccommodationbooking.application.user;

import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import com.flashaccommodationbooking.infrastructure.user.UserJpaRepository;
import com.flashaccommodationbooking.support.BookingTestFixtures;
import com.flashaccommodationbooking.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        userJpaRepository.deleteAll();
        userId = userJpaRepository.save(BookingTestFixtures.defaultUser()).getId();
    }

    @AfterEach
    void tearDown() {
        userJpaRepository.deleteAll();
    }

    @DisplayName("deductPoint() 호출 시 포인트가 차감된다")
    @Test
    @Transactional
    void deductsPoint_whenDeductPointCalled() {
        // act
        userService.deductPoint(userId, 30_000);

        // assert
        assertThat(userJpaRepository.findById(userId).orElseThrow().getPoint()).isEqualTo(120_000);
    }

    @DisplayName("deductPoint() 잔액보다 큰 금액이면 INSUFFICIENT_POINT 예외가 발생한다")
    @Test
    @Transactional
    void throwsException_whenDeductAmountExceedsBalance() {
        // act & assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userService.deductPoint(userId, 200_000);
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINT);
    }

    @DisplayName("addPoint() 호출 시 포인트가 복원된다")
    @Test
    @Transactional
    void addsPoint_whenAddPointCalled() {
        // arrange
        userService.deductPoint(userId, 50_000);

        // act
        userService.addPoint(userId, 20_000);

        // assert
        assertThat(userJpaRepository.findById(userId).orElseThrow().getPoint()).isEqualTo(120_000);
    }
}
