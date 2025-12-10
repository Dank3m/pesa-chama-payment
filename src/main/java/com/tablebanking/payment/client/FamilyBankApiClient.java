package com.tablebanking.payment.client;

import com.tablebanking.payment.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class FamilyBankApiClient {

    private final WebClient webClient;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${familybank.auth.token-url}")
    private String tokenUrl;

    @Value("${familybank.payments.base-url}")
    private String paymentsBaseUrl;

    @Value("${familybank.payments.client-id}")
    private String paymentsClientId;

    @Value("${familybank.payments.client-secret}")
    private String paymentsClientSecret;

    @Value("${familybank.payments.scope}")
    private String paymentsScope;

    @Value("${familybank.collections.client-id}")
    private String collectionsClientId;

    @Value("${familybank.collections.client-secret}")
    private String collectionsClientSecret;

    @Value("${familybank.collections.scope}")
    private String collectionsScope;

    @Value("${familybank.token-cache-ttl:3500}")
    private int tokenCacheTtl;

    private static final String PAYMENTS_TOKEN_KEY = "fbl:token:payments";
    private static final String COLLECTIONS_TOKEN_KEY = "fbl:token:collections";

    public FamilyBankApiClient(WebClient.Builder webClientBuilder, RedisTemplate<String, String> redisTemplate) {
        this.webClient = webClientBuilder.build();
        this.redisTemplate = redisTemplate;
    }

    /**
     * Get OAuth token for Mass Payments API
     */
    public String getPaymentsToken() {
        // Check cache first
        String cachedToken = redisTemplate.opsForValue().get(PAYMENTS_TOKEN_KEY);
        if (cachedToken != null) {
            log.debug("Using cached payments token");
            return cachedToken;
        }

        // Fetch new token
        log.info("Fetching new payments token from Family Bank");
        TokenResponse tokenResponse = fetchToken(paymentsClientId, paymentsClientSecret, paymentsScope);
        
        if (tokenResponse != null && tokenResponse.getAccessToken() != null) {
            // Cache the token
            redisTemplate.opsForValue().set(
                    PAYMENTS_TOKEN_KEY, 
                    tokenResponse.getAccessToken(),
                    Duration.ofSeconds(tokenCacheTtl)
            );
            return tokenResponse.getAccessToken();
        }

        throw new RuntimeException("Failed to obtain payments token from Family Bank");
    }

    /**
     * Get OAuth token for Collections API
     */
    public String getCollectionsToken() {
        String cachedToken = redisTemplate.opsForValue().get(COLLECTIONS_TOKEN_KEY);
        if (cachedToken != null) {
            log.debug("Using cached collections token");
            return cachedToken;
        }

        log.info("Fetching new collections token from Family Bank");
        TokenResponse tokenResponse = fetchToken(collectionsClientId, collectionsClientSecret, collectionsScope);
        
        if (tokenResponse != null && tokenResponse.getAccessToken() != null) {
            redisTemplate.opsForValue().set(
                    COLLECTIONS_TOKEN_KEY, 
                    tokenResponse.getAccessToken(),
                    Duration.ofSeconds(tokenCacheTtl)
            );
            return tokenResponse.getAccessToken();
        }

        throw new RuntimeException("Failed to obtain collections token from Family Bank");
    }

    @Retryable(
            retryFor = {WebClientResponseException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private TokenResponse fetchToken(String clientId, String clientSecret, String scope) {
        try {
            TokenRequest request = TokenRequest.builder()
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .grantType("client_credentials")
                    .scope(scope)
                    .build();

            return webClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block(Duration.ofSeconds(30));

        } catch (Exception e) {
            log.error("Failed to fetch token: {}", e.getMessage());
            throw new RuntimeException("Token fetch failed", e);
        }
    }

    /**
     * Submit bulk payment to Family Bank
     */
    @Retryable(
            retryFor = {WebClientResponseException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public BulkPaymentResponse submitBulkPayment(BulkPaymentRequest request) {
        log.info("Submitting bulk payment to Family Bank: batchRef={}", request.getBatchref());
        
        String token = getPaymentsToken();
        
        try {
            BulkPaymentResponse response = webClient.post()
                    .uri(paymentsBaseUrl + "/api/V1/Transactions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("Client error from FBL: {}", body);
                                    return Mono.error(new RuntimeException("FBL API client error: " + body));
                                });
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("Server error from FBL: {}", body);
                                    return Mono.error(new RuntimeException("FBL API server error: " + body));
                                });
                    })
                    .bodyToMono(BulkPaymentResponse.class)
                    .block(Duration.ofSeconds(60));

            log.info("Bulk payment response: batchRef={}, status={}", 
                    response != null ? response.getBatchref() : null,
                    response != null ? response.getStatus() : null);
            
            return response;

        } catch (WebClientResponseException e) {
            log.error("FBL API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            // Clear token cache on auth errors
            if (e.getStatusCode().value() == 401) {
                redisTemplate.delete(PAYMENTS_TOKEN_KEY);
            }
            throw e;
        }
    }

    /**
     * Query bulk payment status from Family Bank
     */
    @Retryable(
            retryFor = {WebClientResponseException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public BulkPaymentResponse queryBulkPaymentStatus(String batchRef) {
        log.info("Querying bulk payment status: batchRef={}", batchRef);
        
        String token = getPaymentsToken();
        
        try {
            return webClient.get()
                    .uri(paymentsBaseUrl + "/api/V1/Transactions?BatchRef=" + batchRef)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(BulkPaymentResponse.class)
                    .block(Duration.ofSeconds(30));

        } catch (WebClientResponseException e) {
            log.error("FBL API query error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 401) {
                redisTemplate.delete(PAYMENTS_TOKEN_KEY);
            }
            throw e;
        }
    }

    /**
     * Invalidate cached tokens (e.g., on auth failure)
     */
    public void invalidateTokens() {
        redisTemplate.delete(PAYMENTS_TOKEN_KEY);
        redisTemplate.delete(COLLECTIONS_TOKEN_KEY);
        log.info("Invalidated cached FBL tokens");
    }
}
