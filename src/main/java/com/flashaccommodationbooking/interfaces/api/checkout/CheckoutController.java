package com.flashaccommodationbooking.interfaces.api.checkout;

import com.flashaccommodationbooking.application.checkout.CheckoutFacade;
import com.flashaccommodationbooking.application.checkout.CheckoutInfo;
import com.flashaccommodationbooking.global.common.CommonApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Checkout", description = "재고 선점 API")
@RestController
@RequestMapping("/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutFacade checkoutFacade;

    @Operation(summary = "재고 선점", description = "입장 허가된 사용자의 대기열 토큰으로 재고를 선점하고 결제용 checkoutToken을 발급합니다. (TTL 5분)")
    @PostMapping
    public CommonApiResponse<CheckoutV1Dto.CheckoutResponse> reserveStock(
            @RequestBody @Valid CheckoutV1Dto.CheckoutRequest request) {
        String checkoutToken = checkoutFacade.reserveStock(request.queueToken());
        return CommonApiResponse.success(new CheckoutV1Dto.CheckoutResponse(checkoutToken));
    }

    @Operation(summary = "주문서 조회", description = "checkoutToken으로 상품 정보와 포인트 잔액을 조회합니다. checkoutToken 유효 시간(5분) 내에만 접근 가능합니다.")
    @GetMapping("/{checkoutToken}")
    public CommonApiResponse<CheckoutV1Dto.OrderSheetResponse> getOrderSheet(@PathVariable String checkoutToken) {
        CheckoutInfo.OrderSheet orderSheet = checkoutFacade.getOrderSheet(checkoutToken);
        return CommonApiResponse.success(CheckoutV1Dto.OrderSheetResponse.from(orderSheet));
    }

}
