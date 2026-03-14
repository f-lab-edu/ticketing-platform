package com.ticket_service.auth.service;

import com.ticket_service.auth.dto.QueueTokenClaims;
import com.ticket_service.auth.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Service
public class QueueTokenService {

    private static final String CLAIM_CONCERT_ID = "concertId";
    private static final String CLAIM_USER_ID = "userId";

    @Value("${jwt.secret}")
    private String secretString;

    @Value("${jwt.expiration:10m}")
    private Duration expiration;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 대기열 통과 후 api 호출 토큰 생성
     */
    public String generateToken(Long concertId, String userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiration);

        String token = Jwts.builder()
                .claim(CLAIM_CONCERT_ID, concertId)
                .claim(CLAIM_USER_ID, userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();

        log.debug("토큰 생성: concertId={}, userId={}, expiresAt={}", concertId, userId, expiresAt);
        return token;
    }

    /**
     * 토큰 검증 및 클레임 추출
     */
    private QueueTokenClaims validateAndGetClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long concertId = claims.get(CLAIM_CONCERT_ID, Long.class);
            String userId = claims.get(CLAIM_USER_ID, String.class);
            Instant issuedAt = claims.getIssuedAt().toInstant();
            Instant expiresAt = claims.getExpiration().toInstant();

            return QueueTokenClaims.builder()
                    .concertId(concertId)
                    .userId(userId)
                    .issuedAt(issuedAt)
                    .expiresAt(expiresAt)
                    .build();

        } catch (ExpiredJwtException e) {
            log.warn("만료된 토큰: {}", e.getMessage());
            throw InvalidTokenException.expired();
        } catch (JwtException e) {
            log.warn("유효하지 않은 토큰: {}", e.getMessage());
            throw InvalidTokenException.invalid();
        }
    }

    /**
     * 토큰 검증 및 콘서트 ID 일치 확인
     */
    public QueueTokenClaims validateAndGetClaims(String token, Long expectedConcertId) {
        QueueTokenClaims claims = validateAndGetClaims(token);

        if (!claims.getConcertId().equals(expectedConcertId)) {
            throw InvalidTokenException.mismatchedConcert(expectedConcertId, claims.getConcertId());
        }

        return claims;
    }

    /**
     * 토큰에서 사용자 ID 추출 (검증 포함)
     */
    public String extractUserId(String token) {
        return validateAndGetClaims(token).getUserId();
    }

    /**
     * 토큰에서 콘서트 ID 추출 (검증 포함)
     */
    public Long extractConcertId(String token) {
        return validateAndGetClaims(token).getConcertId();
    }
}
