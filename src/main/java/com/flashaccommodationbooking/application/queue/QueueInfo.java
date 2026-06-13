package com.flashaccommodationbooking.application.queue;

import com.flashaccommodationbooking.domain.queue.QueueStatus;

import java.util.Map;

public class QueueInfo {

    public record TokenInfo(Long userId, Long productId, QueueStatus status, long createdAt) {

        public static TokenInfo from(Map<Object, Object> fields) {
            return new TokenInfo(
                    Long.parseLong((String) fields.get("userId")),
                    Long.parseLong((String) fields.get("productId")),
                    QueueStatus.valueOf((String) fields.get("status")),
                    Long.parseLong((String) fields.get("createdAt"))
            );
        }
    }

    public record StatusInfo(QueueStatus status, Long rank) {

        public static StatusInfo waiting(Long rank) {
            return new StatusInfo(QueueStatus.WAITING, rank);
        }

        public static StatusInfo admitted() {
            return new StatusInfo(QueueStatus.ADMITTED, null);
        }
    }

}
