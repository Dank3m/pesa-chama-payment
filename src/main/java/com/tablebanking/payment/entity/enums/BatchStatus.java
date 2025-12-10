package com.tablebanking.payment.entity.enums;

public enum BatchStatus {
    PENDING,
    QUEUED,
    FAILED,
    CBS_POSTED,
    CBS_ACK,
    CBS_COMPLETED,
    CBS_FAILED
}
