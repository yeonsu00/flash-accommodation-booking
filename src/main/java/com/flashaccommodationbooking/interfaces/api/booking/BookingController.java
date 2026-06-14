package com.flashaccommodationbooking.interfaces.api.booking;

import com.flashaccommodationbooking.application.booking.BookingFacade;
import com.flashaccommodationbooking.application.booking.BookingInfo;
import com.flashaccommodationbooking.global.common.CommonApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Booking", description = "결제 및 예약 API")
@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingFacade bookingFacade;

    @Operation(summary = "결제 요청", description = "재고 선점 토큰으로 결제를 요청하고 예약을 확정합니다. Idempotency-Key 헤더로 중복 결제를 방지합니다.")
    @PostMapping
    public CommonApiResponse<BookingV1Dto.BookingResponse> createBooking(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid BookingV1Dto.BookingRequest request) {
        BookingInfo.BookingResult result = bookingFacade.createBooking(request.toCommand(idempotencyKey));
        return CommonApiResponse.success(BookingV1Dto.BookingResponse.from(result));
    }

}
