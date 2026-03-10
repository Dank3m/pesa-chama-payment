package com.tablebanking.payment.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Safaricom Daraja STK Push (Lipa Na M-Pesa Online) API client.
 */
@Component
@Slf4j
public class DarajaStkClient {

    private final DarajaB2CClient darajaB2CClient;
    private final WebClient webClient;

    @Value("${daraja.stk.url:https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest}")
    private String stkUrl;

    @Value("${daraja.stk.query-url:https://sandbox.safaricom.co.ke/mpesa/stkpushquery/v1/query}")
    private String stkQueryUrl;

    @Value("${daraja.stk.business-short-code:174379}")
    private String businessShortCode;

    @Value("${daraja.stk.passkey:}")
    private String passkey;

    @Value("${daraja.stk.callback-url:}")
    private String callbackUrl;

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public DarajaStkClient(DarajaB2CClient darajaB2CClient, WebClient.Builder webClientBuilder) {
        this.darajaB2CClient = darajaB2CClient;
        this.webClient = webClientBuilder.build();
    }

    /**
     * Initiate an STK Push request to the customer's phone.
     */
    @Retryable(
            retryFor = {WebClientResponseException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public StkPushApiResponse initiateStkPush(BigDecimal amount, String phone, String accountRef, String transactionDesc) {
        log.info("Initiating STK Push: amount={}, phone={}, accountRef={}", amount, phone, accountRef);

        String token = darajaB2CClient.getAccessToken();
        String normalizedPhone = darajaB2CClient.normalizePhoneNumber(phone);
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String password = Base64.getEncoder().encodeToString(
                (businessShortCode + passkey + timestamp).getBytes());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("BusinessShortCode", businessShortCode);
        requestBody.put("Password", password);
        requestBody.put("Timestamp", timestamp);
        requestBody.put("TransactionType", "CustomerPayBillOnline");
        requestBody.put("Amount", amount.setScale(0, RoundingMode.CEILING).intValue());
        requestBody.put("PartyA", normalizedPhone);
        requestBody.put("PartyB", businessShortCode);
        requestBody.put("PhoneNumber", normalizedPhone);
        requestBody.put("CallBackURL", callbackUrl);
        requestBody.put("AccountReference", accountRef);
        requestBody.put("TransactionDesc", transactionDesc);

        try {
            StkPushApiResponse response = webClient.post()
                    .uri(stkUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(StkPushApiResponse.class)
                    .block(Duration.ofSeconds(30));

            log.info("STK Push response: merchantRequestId={}, checkoutRequestId={}, responseCode={}",
                    response != null ? response.getMerchantRequestID() : null,
                    response != null ? response.getCheckoutRequestID() : null,
                    response != null ? response.getResponseCode() : null);

            return response;
        } catch (WebClientResponseException e) {
            log.error("Daraja STK Push API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    /**
     * Query the status of an STK Push request.
     */
    @Retryable(
            retryFor = {WebClientResponseException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public StkQueryResponse queryStkPushStatus(String checkoutRequestId) {
        log.info("Querying STK Push status: checkoutRequestId={}", checkoutRequestId);

        String token = darajaB2CClient.getAccessToken();
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String password = Base64.getEncoder().encodeToString(
                (businessShortCode + passkey + timestamp).getBytes());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("BusinessShortCode", businessShortCode);
        requestBody.put("Password", password);
        requestBody.put("Timestamp", timestamp);
        requestBody.put("CheckoutRequestID", checkoutRequestId);

        try {
            StkQueryResponse response = webClient.post()
                    .uri(stkQueryUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(StkQueryResponse.class)
                    .block(Duration.ofSeconds(30));

            log.info("STK Query response: checkoutRequestId={}, resultCode={}",
                    checkoutRequestId,
                    response != null ? response.getResultCode() : null);

            return response;
        } catch (WebClientResponseException e) {
            log.error("Daraja STK Query API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    @Data
    public static class StkPushApiResponse {
        @JsonProperty("MerchantRequestID")
        private String MerchantRequestID;

        @JsonProperty("CheckoutRequestID")
        private String CheckoutRequestID;

        @JsonProperty("ResponseCode")
        private String ResponseCode;

        @JsonProperty("ResponseDescription")
        private String ResponseDescription;

        @JsonProperty("CustomerMessage")
        private String CustomerMessage;
    }

    @Data
    public static class StkQueryResponse {
        @JsonProperty("ResponseCode")
        private String ResponseCode;

        @JsonProperty("ResponseDescription")
        private String ResponseDescription;

        @JsonProperty("MerchantRequestID")
        private String MerchantRequestID;

        @JsonProperty("CheckoutRequestID")
        private String CheckoutRequestID;

        @JsonProperty("ResultCode")
        private String ResultCode;

        @JsonProperty("ResultDesc")
        private String ResultDesc;
    }
}
