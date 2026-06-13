package com.flashaccommodationbooking.application.queue;

import com.flashaccommodationbooking.domain.queue.QueueStatus;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private QueueRepository queueRepository;

    @InjectMocks
    private QueueService queueService;

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;
    private static final String QUEUE_TOKEN = "550e8400-e29b-41d4-a716-446655440000";
    private static final long RECEIVED_AT = 1_700_000_000_000L;

    @DisplayName("enterQueue()")
    @Nested
    class EnterQueue {

        @BeforeEach
        void setUpOpenProduct() {
            when(queueRepository.getProductOpenAt(PRODUCT_ID)).thenReturn(RECEIVED_AT);
        }

        @DisplayName("정상적으로 호출되면, UUID 형식의 대기열 토큰을 반환한다.")
        @Test
        void returnsQueueToken_whenEnterQueueSuccessfully() {
            // act
            String queueToken = queueService.enterQueue(USER_ID, PRODUCT_ID, RECEIVED_AT);

            // assert
            assertThat(UUID.fromString(queueToken)).isNotNull();
        }

        @DisplayName("호출되면, 대기열 추가와 토큰 정보 저장을 각 1회씩 호출한다.")
        @Test
        void callsAddToWaitingQueueAndSaveTokenInfo_whenEnterQueue() {
            // act
            queueService.enterQueue(USER_ID, PRODUCT_ID, RECEIVED_AT);

            // assert
            verify(queueRepository, times(1)).addToWaitingQueue(eq(PRODUCT_ID), anyString(), eq((double) RECEIVED_AT));
            verify(queueRepository, times(1)).saveQueueTokenInfo(anyString(), eq(USER_ID), eq(PRODUCT_ID), eq(RECEIVED_AT));
        }

        @DisplayName("호출되면, receivedAt을 score로 하여 대기열에 등록한다.")
        @Test
        void usesReceivedAtAsScore_whenEnterQueue() {
            // arrange
            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);

            // act
            queueService.enterQueue(USER_ID, PRODUCT_ID, RECEIVED_AT);

            // assert
            verify(queueRepository).addToWaitingQueue(eq(PRODUCT_ID), anyString(), scoreCaptor.capture());
            assertThat(scoreCaptor.getValue()).isEqualTo((double) RECEIVED_AT);
        }

        @DisplayName("오픈 시간 이전이면, QUEUE_NOT_OPEN 예외가 발생한다.")
        @Test
        void throwsException_whenReceivedAtIsBeforeOpenAt() {
            // arrange
            when(queueRepository.getProductOpenAt(PRODUCT_ID)).thenReturn(RECEIVED_AT + 1_000L);

            // act & assert
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                queueService.enterQueue(USER_ID, PRODUCT_ID, RECEIVED_AT);
            });

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.QUEUE_NOT_OPEN);
            verify(queueRepository, never()).addToWaitingQueue(anyLong(), anyString(), eq((double) RECEIVED_AT));
        }

        @DisplayName("오픈 정보가 없으면, QUEUE_NOT_OPEN 예외가 발생한다.")
        @Test
        void throwsException_whenProductOpenAtNotFound() {
            // arrange
            when(queueRepository.getProductOpenAt(PRODUCT_ID)).thenReturn(null);

            // act & assert
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                queueService.enterQueue(USER_ID, PRODUCT_ID, RECEIVED_AT);
            });

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.QUEUE_NOT_OPEN);
        }
    }

    @DisplayName("getStatus() — WAITING")
    @Nested
    class GetStatusWaiting {

        @DisplayName("토큰 상태가 WAITING이면, WAITING 상태와 순번을 반환한다.")
        @Test
        void returnsWaitingStatusWithRank_whenTokenIsWaiting() {
            // arrange
            QueueInfo.TokenInfo tokenInfo = new QueueInfo.TokenInfo(USER_ID, PRODUCT_ID, QueueStatus.WAITING, RECEIVED_AT);
            when(queueRepository.getQueueTokenInfo(QUEUE_TOKEN)).thenReturn(Optional.of(tokenInfo));
            when(queueRepository.getQueueRank(PRODUCT_ID, QUEUE_TOKEN)).thenReturn(2L);

            // act
            QueueInfo.StatusInfo statusInfo = queueService.getStatus(QUEUE_TOKEN);

            // assert
            assertAll(
                    () -> assertThat(statusInfo.status()).isEqualTo(QueueStatus.WAITING),
                    () -> assertThat(statusInfo.rank()).isEqualTo(3L)
            );
        }

        @DisplayName("ZRANK 결과가 0이면, 순번을 1로 반환한다.")
        @Test
        void returnsRankPlusOne_whenRankIsZeroBasedIndex() {
            // arrange
            QueueInfo.TokenInfo tokenInfo = new QueueInfo.TokenInfo(USER_ID, PRODUCT_ID, QueueStatus.WAITING, RECEIVED_AT);
            when(queueRepository.getQueueTokenInfo(QUEUE_TOKEN)).thenReturn(Optional.of(tokenInfo));
            when(queueRepository.getQueueRank(PRODUCT_ID, QUEUE_TOKEN)).thenReturn(0L);

            // act
            QueueInfo.StatusInfo statusInfo = queueService.getStatus(QUEUE_TOKEN);

            // assert
            assertThat(statusInfo.rank()).isEqualTo(1L);
        }

        @DisplayName("ZRANK 결과가 null이면, rank=null로 반환한다.")
        @Test
        void returnsWaitingWithNullRank_whenRankNotFound() {
            // arrange
            QueueInfo.TokenInfo tokenInfo = new QueueInfo.TokenInfo(USER_ID, PRODUCT_ID, QueueStatus.WAITING, RECEIVED_AT);
            when(queueRepository.getQueueTokenInfo(QUEUE_TOKEN)).thenReturn(Optional.of(tokenInfo));
            when(queueRepository.getQueueRank(PRODUCT_ID, QUEUE_TOKEN)).thenReturn(null);

            // act
            QueueInfo.StatusInfo statusInfo = queueService.getStatus(QUEUE_TOKEN);

            // assert
            assertAll(
                    () -> assertThat(statusInfo.status()).isEqualTo(QueueStatus.WAITING),
                    () -> assertThat(statusInfo.rank()).isNull()
            );
        }
    }

    @DisplayName("getStatus() — ADMITTED")
    @Nested
    class GetStatusAdmitted {

        @DisplayName("토큰 상태가 ADMITTED이면, ADMITTED 상태와 rank=null을 반환한다.")
        @Test
        void returnsAdmittedStatus_whenTokenIsAdmitted() {
            // arrange
            QueueInfo.TokenInfo tokenInfo = new QueueInfo.TokenInfo(USER_ID, PRODUCT_ID, QueueStatus.ADMITTED, RECEIVED_AT);
            when(queueRepository.getQueueTokenInfo(QUEUE_TOKEN)).thenReturn(Optional.of(tokenInfo));

            // act
            QueueInfo.StatusInfo statusInfo = queueService.getStatus(QUEUE_TOKEN);

            // assert
            assertAll(
                    () -> assertThat(statusInfo.status()).isEqualTo(QueueStatus.ADMITTED),
                    () -> assertThat(statusInfo.rank()).isNull()
            );
        }

        @DisplayName("토큰 상태가 ADMITTED이면, 순번 조회를 호출하지 않는다.")
        @Test
        void doesNotCallGetQueueRank_whenTokenIsAdmitted() {
            // arrange
            QueueInfo.TokenInfo tokenInfo = new QueueInfo.TokenInfo(USER_ID, PRODUCT_ID, QueueStatus.ADMITTED, RECEIVED_AT);
            when(queueRepository.getQueueTokenInfo(QUEUE_TOKEN)).thenReturn(Optional.of(tokenInfo));

            // act
            queueService.getStatus(QUEUE_TOKEN);

            // assert
            verify(queueRepository, never()).getQueueRank(anyLong(), anyString());
        }
    }

    @DisplayName("admitFromQueue()")
    @Nested
    class AdmitFromQueue {

        @DisplayName("대기 토큰이 있으면, ADMISSION_BATCH_SIZE만큼 admitted 처리한다.")
        @Test
        void admitsWaitingTokens_whenTokensExist() {
            // arrange
            Set<String> tokens = new LinkedHashSet<>(Set.of("token-1", "token-2"));
            when(queueRepository.getWaitingTokens(PRODUCT_ID, 5)).thenReturn(tokens);

            // act
            queueService.admitFromQueue(PRODUCT_ID);

            // assert
            verify(queueRepository).admitTokens(PRODUCT_ID, tokens);
            verify(queueRepository).updateTokenStatus("token-1", QueueStatus.ADMITTED);
            verify(queueRepository).updateTokenStatus("token-2", QueueStatus.ADMITTED);
            verify(queueRepository).removeFromWaitingQueue(PRODUCT_ID, tokens);
        }

        @DisplayName("대기 토큰이 없으면, admitted 처리를 호출하지 않는다.")
        @Test
        void doesNothing_whenWaitingTokensEmpty() {
            // arrange
            when(queueRepository.getWaitingTokens(PRODUCT_ID, 5)).thenReturn(Collections.emptySet());

            // act
            queueService.admitFromQueue(PRODUCT_ID);

            // assert
            verify(queueRepository, never()).admitTokens(anyLong(), eq(Collections.emptySet()));
            verify(queueRepository, never()).updateTokenStatus(anyString(), eq(QueueStatus.ADMITTED));
            verify(queueRepository, never()).removeFromWaitingQueue(anyLong(), eq(Collections.emptySet()));
        }

        @DisplayName("admitted 처리 시, admitTokens → 상태 변경 → 대기열 삭제 순서로 호출한다.")
        @Test
        void processesInOrder_whenAdmittingTokens() {
            // arrange
            Set<String> tokens = Set.of(QUEUE_TOKEN);
            when(queueRepository.getWaitingTokens(PRODUCT_ID, 5)).thenReturn(tokens);
            var inOrder = inOrder(queueRepository);

            // act
            queueService.admitFromQueue(PRODUCT_ID);

            // assert
            inOrder.verify(queueRepository).getWaitingTokens(PRODUCT_ID, 5);
            inOrder.verify(queueRepository).admitTokens(PRODUCT_ID, tokens);
            inOrder.verify(queueRepository).updateTokenStatus(QUEUE_TOKEN, QueueStatus.ADMITTED);
            inOrder.verify(queueRepository).removeFromWaitingQueue(PRODUCT_ID, tokens);
        }
    }

    @DisplayName("getStatus() — 예외")
    @Nested
    class GetStatusException {

        @DisplayName("존재하지 않는 토큰이면, QUEUE_TOKEN_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenQueueTokenNotFound() {
            // arrange
            when(queueRepository.getQueueTokenInfo(QUEUE_TOKEN)).thenReturn(Optional.empty());

            // act & assert
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                queueService.getStatus(QUEUE_TOKEN);
            });

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.QUEUE_TOKEN_NOT_FOUND);
            verify(queueRepository, never()).getQueueRank(anyLong(), anyString());
        }
    }
}
