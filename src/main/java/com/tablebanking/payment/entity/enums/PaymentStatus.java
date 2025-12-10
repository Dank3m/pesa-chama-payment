package com.tablebanking.payment.entity.enums;

public enum PaymentStatus {
    RECEIVED,
    VALIDATING,
    VALIDATED,
    VALIDATION_FAILED,
    PROCESSING,
    ALLOCATED,
    PARTIALLY_ALLOCATED,
    COMPLETED,
    FAILED,
    REVERSED
}
