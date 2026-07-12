package com.tryna.global.security.jwt;

import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String USER_ID_CLAIM = "userId";

    private final JwtProperties jwtProperties;
    private final Key signingKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId) {
        return createToken(userId, jwtProperties.getAccessExpiration(), TokenType.ACCESS);
    }

    public String generateRefreshToken(Long userId) {
        return createToken(userId, jwtProperties.getRefreshExpiration(), TokenType.REFRESH);
    }

    public TokenPair generateTokenPair(Long userId) {
        return new TokenPair(generateAccessToken(userId), generateRefreshToken(userId));
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(AuthErrorCode.AUTH_401_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.AUTH_401_INVALID_TOKEN);
        }
    }

    public boolean validateToken(String token) {
        parseClaims(token);
        return true;
    }

    public boolean validateToken(String token, TokenType expectedType) {
        Claims claims = parseClaims(token);
        if (!expectedType.name().equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new BusinessException(AuthErrorCode.AUTH_401_INVALID_TOKEN_TYPE);
        }
        return true;
    }

    public Long getUserId(String token) {
        Claims claims = parseClaims(token);
        Number userId = claims.get(USER_ID_CLAIM, Number.class);
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.AUTH_401_INVALID_TOKEN);
        }
        return userId.longValue();
    }

    public long getRefreshExpirationSeconds() {
        return jwtProperties.getRefreshExpiration();
    }

    private String createToken(Long userId, long expirationSeconds, TokenType tokenType) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .setIssuer(jwtProperties.getIssuer())
                .setSubject(String.valueOf(userId))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .claim(USER_ID_CLAIM, userId)
                .claim(TOKEN_TYPE_CLAIM, tokenType.name())
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // AccessToken 만료 시간 조회 (A102 응답용)
    public long getAccessExpirationSeconds() {
        return jwtProperties.getAccessExpiration();
    }
}
