package com.tablebanking.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DarajaB2CRequest {

    @JsonProperty("OriginatorConversationID")
    private String originatorConversationId;

    @JsonProperty("InitiatorName")
    private String initiatorName;

    @JsonProperty("SecurityCredential")
    private String securityCredential;

    @JsonProperty("CommandID")
    private String commandId;

    @JsonProperty("Amount")
    private Integer amount;

    @JsonProperty("PartyA")
    private String partyA;

    @JsonProperty("PartyB")
    private String partyB;

    @JsonProperty("Remarks")
    private String remarks;

    @JsonProperty("QueueTimeOutURL")
    private String queueTimeOutUrl;

    @JsonProperty("ResultURL")
    private String resultUrl;

    @JsonProperty("Occasion")
    private String occasion;
}