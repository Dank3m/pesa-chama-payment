package com.tablebanking.payment.entity.enums;

public enum DisbursementStatus {
    PENDING,
    QUEUED,
    FAILED,
    CBS_POSTED,
    CBS_ACK,
    CBS_COMPLETED,
    CBS_FAILED,
    THIRDPARTY_POSTED,
    THIRDPARTY_ACK,
    THIRDPARTY_COMPLETED,
    THIRDPARTY_FAILED
}
