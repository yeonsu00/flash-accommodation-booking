package com.flashaccommodationbooking.application.booking;

import com.flashaccommodationbooking.application.booking.event.BookingCompletedEvent;
import com.flashaccommodationbooking.application.booking.event.BookingFailedEvent;
import com.flashaccommodationbooking.application.checkout.CheckoutInfo;
import com.flashaccommodationbooking.application.checkout.CheckoutService;
import com.flashaccommodationbooking.application.payment.PaymentService;
import com.flashaccommodationbooking.application.product.ProductService;
import com.flashaccommodationbooking.application.user.UserService;
import com.flashaccommodationbooking.domain.booking.Booking;
import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingFacade {

    private final CheckoutService checkoutService;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final ProductService productService;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public BookingInfo.BookingResult createBooking(BookingCommand.BookingRequestCommand command) {
        if (!bookingService.setIdempotencyProcessing(command.idempotencyKey())) {
            return bookingService.getBookingResult(command.idempotencyKey());
        }

        try {
            CheckoutInfo.TokenInfo tokenInfo = checkoutService.getCheckoutTokenInfo(command.checkoutToken());
            paymentService.validatePaymentCombination(command.paymentMethods());

            AccommodationProduct product = productService.getProduct(tokenInfo.productId());
            productService.validateTotalAmount(command.paymentMethods(), product.getPrice());

            User user = userService.getUser(tokenInfo.userId());
            Booking booking = bookingService.createBooking(user, product);

            paymentService.processPayments(booking.getId(), command.idempotencyKey(), command.paymentMethods(), tokenInfo.userId());

            bookingService.confirm(booking);

            log.info("예약 완료 - checkoutToken 삭제 및 멱등성 결과 저장 예정 [bookingId: {}, idempotencyKey: {}]", booking.getId(), command.idempotencyKey());
            eventPublisher.publishEvent(new BookingCompletedEvent(command.checkoutToken(), command.idempotencyKey(), booking.getId()));

            return BookingInfo.BookingResult.of(booking.getId());

        } catch (Exception e) {
            log.warn("예약 실패 - 멱등키 삭제 예정 [idempotencyKey: {}, reason: {}]", command.idempotencyKey(), e.getMessage());
            eventPublisher.publishEvent(new BookingFailedEvent(command.idempotencyKey()));
            throw e;
        }
    }

}
