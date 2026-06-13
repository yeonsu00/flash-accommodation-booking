package com.flashaccommodationbooking.interfaces.api.queue;

import com.flashaccommodationbooking.application.queue.QueueInfo;
import com.flashaccommodationbooking.application.queue.QueueService;
import com.flashaccommodationbooking.global.common.CommonApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Queue", description = "대기열 API")
@RestController
@RequestMapping("/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @Operation(summary = "대기열 등록", description = "사용자를 상품 대기열에 등록하고 대기열 토큰을 발급합니다.")
    @PostMapping("/enter")
    public CommonApiResponse<QueueV1Dto.EnterResponse> enter(@RequestBody @Valid QueueV1Dto.EnterRequest request) {
        long receivedAt = System.currentTimeMillis();
        String queueToken = queueService.enterQueue(request.userId(), request.productId(), receivedAt);
        return CommonApiResponse.success(new QueueV1Dto.EnterResponse(queueToken));
    }

    @Operation(summary = "대기열 순번 조회", description = "대기열 토큰으로 현재 상태(WAITING/ADMITTED)와 순번을 조회합니다.")
    @GetMapping("/status")
    public CommonApiResponse<QueueV1Dto.StatusResponse> status(@RequestParam String queueToken) {
        QueueInfo.StatusInfo info = queueService.getStatus(queueToken);
        return CommonApiResponse.success(new QueueV1Dto.StatusResponse(info.status(), info.rank()));
    }
}
