package com.flashaccommodationbooking.application.payment;

import java.util.List;

public record PaymentCompensationEvent(List<PaymentCommand.Request> pgProcessedCommands) {}
