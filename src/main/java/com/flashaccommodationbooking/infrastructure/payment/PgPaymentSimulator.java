package com.flashaccommodationbooking.infrastructure.payment;

import com.flashaccommodationbooking.application.payment.PaymentCommand;
import com.flashaccommodationbooking.global.exception.PgNetworkException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class PgPaymentSimulator {

    // 0~5: 성공(60%), 6~8: 네트워크 오류(30%), 9: 타임아웃(10%)
    private static final int SUCCESS_THRESHOLD = 6;
    private static final int NETWORK_ERROR_THRESHOLD = 9;

    public String approve(PaymentCommand.Request command) {
        int roll = nextRoll();

        if (roll < SUCCESS_THRESHOLD) {
            String pgTransactionId = UUID.randomUUID().toString();
            log.info("PG 승인 성공 [bookingId: {}, methodType: {}, pgTransactionId: {}]",
                    command.bookingId(), command.methodType(), pgTransactionId);
            return pgTransactionId;
        }

        if (roll < NETWORK_ERROR_THRESHOLD) {
            log.warn("PG 승인 실패 - 네트워크 오류 [bookingId: {}, methodType: {}]",
                    command.bookingId(), command.methodType());
            throw new PgNetworkException("PG 승인 실패: 네트워크 오류");
        }

        log.warn("PG 승인 실패 - 타임아웃 [bookingId: {}, methodType: {}]",
                command.bookingId(), command.methodType());
        simulateTimeout();
        throw new PgNetworkException("PG 승인 실패: 응답 타임아웃");
    }

    public void cancel(PaymentCommand.Request command) {
        int roll = nextRoll();

        if (roll < SUCCESS_THRESHOLD) {
            log.info("PG 취소 성공 [bookingId: {}, methodType: {}]",
                    command.bookingId(), command.methodType());
            return;
        }

        if (roll < NETWORK_ERROR_THRESHOLD) {
            log.warn("PG 취소 실패 - 네트워크 오류 [bookingId: {}, methodType: {}]",
                    command.bookingId(), command.methodType());
            throw new PgNetworkException("PG 취소 실패: 네트워크 오류");
        }

        log.warn("PG 취소 실패 - 타임아웃 [bookingId: {}, methodType: {}]",
                command.bookingId(), command.methodType());
        simulateTimeout();
        throw new PgNetworkException("PG 취소 실패: 응답 타임아웃");
    }

    int nextRoll() {
        return ThreadLocalRandom.current().nextInt(10);
    }

    private void simulateTimeout() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
