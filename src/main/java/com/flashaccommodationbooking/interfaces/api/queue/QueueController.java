package com.flashaccommodationbooking.interfaces.api.queue;

import com.flashaccommodationbooking.application.queue.QueueInfo;
import com.flashaccommodationbooking.application.queue.QueueService;
import com.flashaccommodationbooking.global.common.CommonApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/enter")
    public CommonApiResponse<QueueV1Dto.EnterResponse> enter(@RequestBody @Valid QueueV1Dto.EnterRequest request) {
        long receivedAt = System.currentTimeMillis();
        String queueToken = queueService.enterQueue(request.userId(), request.productId(), receivedAt);
        return CommonApiResponse.success(new QueueV1Dto.EnterResponse(queueToken));
    }

    @GetMapping("/status")
    public CommonApiResponse<QueueV1Dto.StatusResponse> status(@RequestParam String queueToken) {
        QueueInfo.StatusInfo info = queueService.getStatus(queueToken);
        return CommonApiResponse.success(new QueueV1Dto.StatusResponse(info.status(), info.rank()));
    }
}
