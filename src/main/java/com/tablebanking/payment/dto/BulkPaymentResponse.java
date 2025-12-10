package com.tablebanking.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response from Family Bank for bulk payment
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BulkPaymentResponse {
    
    private String batchref;
    private String cbsref;
    private String accountdr;
    private String narration;
    private String valuedate;
    private String currency;
    private BigDecimal totalamount;
    private String status;
    private String statusdescription;
    private List<DisbursementDetailResponse> dtl;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DisbursementDetailResponse {
        private String batchref;
        private String paymentref;
        private String externalref;
        private String cbsref;
        private String currency;
        private String xrate;
        private BigDecimal paymentamount;
        private String status;
        private String statusdescription;
    }
}
