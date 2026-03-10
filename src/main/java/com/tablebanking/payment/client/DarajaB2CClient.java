package com.tablebanking.payment.client;

import com.tablebanking.payment.dto.DarajaB2CRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Safaricom Daraja B2C API client for M-Pesa disbursements.
 */
@Component
@Slf4j
public class DarajaB2CClient {

    private final WebClient webClient;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${daraja.auth.url:https://sandbox.safaricom.co.ke/oauth/v1/generate}")
    private String authUrl;

    @Value("${daraja.consumer-key:}")
    private String consumerKey;

    @Value("${daraja.consumer-secret:}")
    private String consumerSecret;

    @Value("${daraja.b2c.url:https://sandbox.safaricom.co.ke/mpesa/b2c/v3/paymentrequest}")
    private String b2cUrl;

    @Value("${daraja.b2c.initiator-name:}")
    private String initiatorName;

    @Value("${daraja.b2c.security-credential:}")
    private String securityCredential;

    @Value("${daraja.b2c.command-id:BusinessPayment}")
    private String commandId;

    @Value("${daraja.b2c.party-a:}")
    private String partyA;

    @Value("${daraja.b2c.queue-timeout-url:}")
    private String queueTimeoutUrl;

    @Value("${daraja.b2c.result-url:}")
    private String resultUrl;

    private static final String TOKEN_CACHE_KEY = "daraja:token:b2c";

    public DarajaB2CClient(WebClient.Builder webClientBuilder, RedisTemplate<String, String> redisTemplate) {
        this.webClient = webClientBuilder.build();
        this.redisTemplate = redisTemplate;
    }

    /**
     * Get OAuth access token from Daraja, cached in Redis.
     */
    @SuppressWarnings("unchecked")
    public String getAccessToken() {
        String cachedToken = redisTemplate.opsForValue().get(TOKEN_CACHE_KEY);
        if (cachedToken != null) {
            log.debug("Using cached Daraja B2C token");
            return cachedToken;
        }

        log.info("Fetching new Daraja B2C OAuth token");
        String credentials = Base64.getEncoder().encodeToString((consumerKey + ":" + consumerSecret).getBytes());

        try {
            Map<String, Object> response = webClient.get()
                    .uri(authUrl + "?grant_type=client_credentials")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            if (response != null && response.containsKey("access_token")) {
                String token = (String) response.get("access_token");
                // Cache for 4 minutes (Daraja tokens valid for ~5 min)
                redisTemplate.opsForValue().set(TOKEN_CACHE_KEY, token, Duration.ofMinutes(4));
                return token;
            }
        } catch (Exception e) {
            log.error("Failed to fetch Daraja OAuth token: {}", e.getMessage());
        }

        throw new RuntimeException("Failed to obtain Daraja B2C access token");
    }

    /**
     * Send B2C payment via Daraja API.
     */
    @Retryable(
            retryFor = {WebClientResponseException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public B2CResponse sendB2CPayment(BigDecimal amount, String phoneNumber, String remarks, String originatorConversationId) {
        log.info("Sending B2C payment: amount={}, phone={}, ocId={}", amount, phoneNumber, originatorConversationId);

        String token = getAccessToken();
        String normalizedPhone = normalizePhoneNumber(phoneNumber);

        DarajaB2CRequest requestBody = DarajaB2CRequest.builder()
                .originatorConversationId(originatorConversationId)
                .initiatorName(initiatorName)
                .securityCredential(securityCredential)
                .commandId(commandId)
                .amount(amount.setScale(0, RoundingMode.CEILING).intValue())
                .partyA(partyA)
                .partyB(normalizedPhone)
                .remarks(remarks != null ? remarks : "Loan Disbursement")
                .queueTimeOutUrl(queueTimeoutUrl)
                .resultUrl(resultUrl)
                .occasion("LoanDisbursement")
                .build();

        try {
            B2CResponse response = webClient.post()
                    .uri(b2cUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.value() == 401, clientResponse -> {
                        redisTemplate.delete(TOKEN_CACHE_KEY);
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new WebClientResponseException(
                                        401, "Unauthorized", null, body.getBytes(), null)));
                    })
                    .bodyToMono(B2CResponse.class)
                    .block(Duration.ofSeconds(30));

            log.info("B2C response: conversationId={}, responseCode={}",
                    response != null ? response.getConversationID() : null,
                    response != null ? response.getResponseCode() : null);

            return response;
        } catch (WebClientResponseException e) {
            log.error("Daraja B2C API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 401) {
                redisTemplate.delete(TOKEN_CACHE_KEY);
            }
            throw e;
        }
    }

    /**
     * Normalize Kenyan phone number to 254xxx format.
     */
    public String normalizePhoneNumber(String phone) {
        if (phone == null) return null;
        phone = phone.trim().replaceAll("[\\s-]", "");
        if (phone.startsWith("+254")) {
            return phone.substring(1);
        } else if (phone.startsWith("0")) {
            return "254" + phone.substring(1);
        } else if (phone.startsWith("254")) {
            return phone;
        }
        return phone;
    }

    @Data
    public static class B2CResponse {
        private String ConversationID;
        private String OriginatorConversationID;
        private String ResponseCode;
        private String ResponseDescription;
    }
}
