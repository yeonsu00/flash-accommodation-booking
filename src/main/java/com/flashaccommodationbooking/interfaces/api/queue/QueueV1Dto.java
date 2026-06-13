package com.flashaccommodationbooking.interfaces.api.queue;

import com.flashaccommodationbooking.domain.queue.QueueStatus;
import jakarta.validation.constraints.NotNull;

public class QueueV1Dto {

    public record EnterRequest(
            @NotNull Long userId,
            @NotNull Long productId
    ) {}

    public record EnterResponse(String queueToken) {}

    public record StatusResponse(QueueStatus status, Long rank) {}
}
