package com.tablebanking.payment.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tablebanking.payment.dto.MemberInfo;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service to look up member information from the main table-banking-app.
 * Uses JWT Bearer token authentication with token caching in Redis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberLookupService {

    private final WebClient.Builder webClientBuilder;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${main-app.base-url:http://localhost:8080}")
    private String mainAppBaseUrl;

    @Value("${main-app.auth.username:}")
    private String serviceUsername;

    @Value("${main-app.auth.password:}")
    private String servicePassword;

    @Value("${main-app.token-cache-ttl:3500}")
    private long tokenCacheTtlSeconds;

    private static final String TOKEN_CACHE_KEY = "payment-service:auth:token";
    private static final String MEMBER_CACHE_PREFIX = "member:lookup:";
    private static final Duration MEMBER_CACHE_TTL = Duration.ofMinutes(5);

    // ==================== Token Management ====================

    /**
     * Get valid access token, from cache or by authenticating.
     */
    private String getAccessToken() {
        // Check cache first
        String cachedToken = (String) redisTemplate.opsForValue().get(TOKEN_CACHE_KEY);
        if (cachedToken != null) {
            log.debug("Using cached access token");
            return cachedToken;
        }

        // Authenticate and get new token
        return refreshAccessToken();
    }

    /**
     * Authenticate with main app and cache the token.
     */
    private synchronized String refreshAccessToken() {
        // Double-check after acquiring lock
        String cachedToken = (String) redisTemplate.opsForValue().get(TOKEN_CACHE_KEY);
        if (cachedToken != null) {
            return cachedToken;
        }

        log.info("Authenticating with main app to get access token");

        try {
            WebClient webClient = webClientBuilder.baseUrl(mainAppBaseUrl).build();

            AuthRequest authRequest = new AuthRequest();
            authRequest.setUsername(serviceUsername);
            authRequest.setPassword(servicePassword);

            ApiResponseWrapper response = webClient.post()
                    .uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(authRequest)
                    .retrieve()
                    .bodyToMono(ApiResponseWrapper.class)
                    .block(Duration.ofSeconds(10));

            if (response != null && response.isSuccess() && response.getData() != null) {
                AuthResponseData authData = response.getData();
                String token = authData.getAccessToken();

                if (token == null || token.isBlank()) {
                    log.error("Authentication failed: no access token in response");
                    throw new RuntimeException("Failed to authenticate with main app: no token");
                }

                // Calculate TTL: use configured TTL or token expiry (whichever is smaller)
                long ttlSeconds = tokenCacheTtlSeconds;
                if (authData.getExpiresIn() != null && authData.getExpiresIn() > 0) {
                    // expiresIn is in milliseconds, convert to seconds and use 90% for safety
                    long actualTtlSeconds = (authData.getExpiresIn() / 1000) * 9 / 10;
                    ttlSeconds = Math.min(ttlSeconds, actualTtlSeconds);
                }

                // Cache the token
                redisTemplate.opsForValue().set(TOKEN_CACHE_KEY, token, ttlSeconds, TimeUnit.SECONDS);
                log.info("Access token cached with TTL: {} seconds", ttlSeconds);

                return token;
            }

            String errorMsg = response != null ? response.getMessage() : "empty response";
            log.error("Authentication failed: {}", errorMsg);
            throw new RuntimeException("Failed to authenticate with main app: " + errorMsg);

        } catch (WebClientResponseException e) {
            log.error("Authentication failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Authentication error: {}", e.getMessage(), e);
            throw new RuntimeException("Authentication error: " + e.getMessage(), e);
        }
    }

    /**
     * Invalidate the cached token (e.g., on 401 response).
     */
    public void invalidateToken() {
        redisTemplate.delete(TOKEN_CACHE_KEY);
        log.info("Access token cache invalidated");
    }

    /**
     * Execute API call with automatic token refresh on 401.
     */
    private <T> T executeWithAuth(ApiCall<T> apiCall) {
        try {
            return apiCall.execute(getAccessToken());
        } catch (WebClientResponseException.Unauthorized e) {
            log.warn("Token expired, refreshing and retrying");
            invalidateToken();
            return apiCall.execute(refreshAccessToken());
        }
    }

    @FunctionalInterface
    private interface ApiCall<T> {
        T execute(String token);
    }

    // ==================== Member Lookup Methods ====================

    /**
     * Find member by identifier (ID number, phone, etc.)
     */
    public MemberInfo findMember(String identifier, String identifierType) {
        log.info("Looking up member: identifier={}, type={}", identifier, identifierType);

        // Check cache first
        String cacheKey = MEMBER_CACHE_PREFIX + identifierType + ":" + identifier;
        MemberInfo cached = (MemberInfo) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Member found in cache: {}", cached.getMemberId());
            return cached;
        }

        try {
            MemberInfo memberInfo = executeWithAuth(token -> {
                WebClient webClient = webClientBuilder.baseUrl(mainAppBaseUrl).build();

                // Map identifier type to API parameter
                String queryParam = switch (identifierType.toUpperCase()) {
                    case "ID_NUMBER" -> "idNumber";
                    case "MSISDN" -> "phoneNumber";
                    case "ACCOUNT_NUMBER" -> "memberId";
                    default -> "identifier";
                };

                return webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/v1/members/lookup")
                                .queryParam(queryParam, identifier)
                                .build())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(MemberInfo.class)
                        .block(Duration.ofSeconds(10));
            });

            if (memberInfo != null && memberInfo.getMemberId() != null) {
                // Cache the result
                redisTemplate.opsForValue().set(cacheKey, memberInfo, MEMBER_CACHE_TTL);
                log.info("Member found: memberId={}, name={}",
                        memberInfo.getMemberId(), memberInfo.getMemberName());
            }

            return memberInfo;

        } catch (Exception e) {
            log.error("Failed to look up member: identifier={}, error={}", identifier, e.getMessage());
            return null;
        }
    }

    /**
     * Find member by member ID
     */
    public MemberInfo findMemberById(UUID memberId) {
        log.info("Looking up member by ID: {}", memberId);

        String cacheKey = MEMBER_CACHE_PREFIX + "id:" + memberId;
        MemberInfo cached = (MemberInfo) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Member found in cache: {}", cached.getMemberId());
            return cached;
        }

        try {
            MemberInfo memberInfo = executeWithAuth(token -> {
                WebClient webClient = webClientBuilder.baseUrl(mainAppBaseUrl).build();

                return webClient.get()
                        .uri("/api/v1/members/{memberId}/financial-status", memberId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(MemberInfo.class)
                        .block(Duration.ofSeconds(10));
            });

            if (memberInfo != null) {
                // Cache the result
                redisTemplate.opsForValue().set(cacheKey, memberInfo, MEMBER_CACHE_TTL);
                log.info("Member found: memberId={}, name={}",
                        memberInfo.getMemberId(), memberInfo.getMemberName());
            }

            return memberInfo;

        } catch (Exception e) {
            log.error("Failed to look up member by ID: memberId={}, error={}", memberId, e.getMessage());

            // Fallback: try to get basic member info without financial status
            return findMemberBasic(memberId);
        }
    }

    /**
     * Get basic member info (fallback when financial status is not available)
     */
    private MemberInfo findMemberBasic(UUID memberId) {
        try {
            return executeWithAuth(token -> {
                WebClient webClient = webClientBuilder.baseUrl(mainAppBaseUrl).build();

                return webClient.get()
                        .uri("/api/v1/members/{memberId}", memberId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(MemberInfo.class)
                        .block(Duration.ofSeconds(10));
            });

        } catch (Exception e) {
            log.error("Failed to get basic member info: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Invalidate cached member info
     */
    public void invalidateMemberCache(UUID memberId) {
        redisTemplate.delete(MEMBER_CACHE_PREFIX + "id:" + memberId);
        log.info("Invalidated cache for member: {}", memberId);
    }

    /**
     * Get member's outstanding contribution amount.
     * Used for payment allocation.
     */
    public java.math.BigDecimal getOutstandingContribution(UUID memberId) {
        MemberInfo memberInfo = findMemberById(memberId);
        return memberInfo != null ? memberInfo.getOutstandingContribution() : null;
    }

    /**
     * Get member's outstanding loan balance.
     * Used for payment allocation.
     */
    public java.math.BigDecimal getOutstandingLoanBalance(UUID memberId) {
        MemberInfo memberInfo = findMemberById(memberId);
        return memberInfo != null ? memberInfo.getOutstandingLoanBalance() : null;
    }

    // ==================== Auth DTOs ====================

    @Data
    private static class AuthRequest {
        private String username;
        private String password;
    }

    /**
     * Wrapper for the API response from main app.
     */
    @Data
    private static class ApiResponseWrapper {
        private boolean success;
        private String message;
        private AuthResponseData data;
    }

    @Data
    private static class AuthResponseData {
        @JsonProperty("accessToken")
        private String accessToken;

        @JsonProperty("refreshToken")
        private String refreshToken;

        @JsonProperty("expiresIn")
        private Long expiresIn;

        @JsonProperty("tokenType")
        private String tokenType;
    }
}