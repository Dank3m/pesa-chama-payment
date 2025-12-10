package com.tablebanking.payment.service;

import com.tablebanking.payment.dto.MemberInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.UUID;

/**
 * Service to look up member information from the main table-banking-app
 * Uses HTTP calls to the main app's API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberLookupService {

    private final WebClient.Builder webClientBuilder;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${main-app.base-url:http://localhost:8080}")
    private String mainAppBaseUrl;

    @Value("${main-app.api-key:}")
    private String mainAppApiKey;

    private static final String MEMBER_CACHE_PREFIX = "member:lookup:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

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
            WebClient webClient = webClientBuilder.baseUrl(mainAppBaseUrl).build();

            // Map identifier type to API parameter
            String queryParam = switch (identifierType.toUpperCase()) {
                case "ID_NUMBER" -> "idNumber";
                case "MSISDN" -> "phoneNumber";
                case "ACCOUNT_NUMBER" -> "memberId";
                default -> "identifier";
            };

            MemberInfo memberInfo = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/members/lookup")
                            .queryParam(queryParam, identifier)
                            .build())
                    .header("X-API-Key", mainAppApiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(MemberInfo.class)
                    .block(Duration.ofSeconds(10));

            if (memberInfo != null && memberInfo.getMemberId() != null) {
                // Cache the result
                redisTemplate.opsForValue().set(cacheKey, memberInfo, CACHE_TTL);
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
            WebClient webClient = webClientBuilder.baseUrl(mainAppBaseUrl).build();

            MemberInfo memberInfo = webClient.get()
                    .uri("/api/v1/members/{memberId}/financial-status", memberId)
                    .header("X-API-Key", mainAppApiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(MemberInfo.class)
                    .block(Duration.ofSeconds(10));

            if (memberInfo != null) {
                // Cache the result
                redisTemplate.opsForValue().set(cacheKey, memberInfo, CACHE_TTL);
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
            WebClient webClient = webClientBuilder.baseUrl(mainAppBaseUrl).build();

            return webClient.get()
                    .uri("/api/v1/members/{memberId}", memberId)
                    .header("X-API-Key", mainAppApiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(MemberInfo.class)
                    .block(Duration.ofSeconds(10));

        } catch (Exception e) {
            log.error("Failed to get basic member info: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Invalidate cached member info
     */
    public void invalidateCache(UUID memberId) {
        String pattern = MEMBER_CACHE_PREFIX + "*" + memberId + "*";
        // Note: In production, use scan for pattern matching
        redisTemplate.delete(MEMBER_CACHE_PREFIX + "id:" + memberId);
        log.info("Invalidated cache for member: {}", memberId);
    }

    /**
     * Get member's outstanding contribution amount
     * Used for payment allocation
     */
    public java.math.BigDecimal getOutstandingContribution(UUID memberId) {
        MemberInfo memberInfo = findMemberById(memberId);
        return memberInfo != null ? memberInfo.getOutstandingContribution() : null;
    }

    /**
     * Get member's outstanding loan balance
     * Used for payment allocation
     */
    public java.math.BigDecimal getOutstandingLoanBalance(UUID memberId) {
        MemberInfo memberInfo = findMemberById(memberId);
        return memberInfo != null ? memberInfo.getOutstandingLoanBalance() : null;
    }
}
