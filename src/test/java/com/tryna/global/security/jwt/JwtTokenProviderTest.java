package com.tryna.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    @Test
    void accessTokenGenerationAndParsingWorks() {
        JwtTokenProvider provider = new JwtTokenProvider(createProperties("test-secret-key-test-secret-key-1234", 1800L, 1209600L, "tryna-test"));

        String accessToken = provider.generateAccessToken(1L);

        assertThat(provider.validateToken(accessToken)).isTrue();
        assertThat(provider.validateToken(accessToken, TokenType.ACCESS)).isTrue();
        assertThat(provider.getUserId(accessToken)).isEqualTo(1L);

        Claims claims = provider.parseClaims(accessToken);
        assertThat(claims.getIssuer()).isEqualTo("tryna-test");
        assertThat(claims.get("tokenType", String.class)).isEqualTo(TokenType.ACCESS.name());
    }

    @Test
    void tokenPairContainsAccessAndRefreshTokens() {
        JwtTokenProvider provider = new JwtTokenProvider(createProperties("test-secret-key-test-secret-key-5678", 1800L, 1209600L, "tryna-test"));

        TokenPair tokenPair = provider.generateTokenPair(10L);

        assertThat(tokenPair.accessToken()).isNotBlank();
        assertThat(tokenPair.refreshToken()).isNotBlank();
        assertThat(provider.validateToken(tokenPair.accessToken(), TokenType.ACCESS)).isTrue();
        assertThat(provider.validateToken(tokenPair.refreshToken(), TokenType.REFRESH)).isTrue();
    }

    @Test
    void expiredTokenThrowsBusinessException() {
        JwtTokenProvider provider = new JwtTokenProvider(createProperties("test-secret-key-test-secret-key-9999", -1L, 1209600L, "tryna-test"));
        String token = provider.generateAccessToken(1L);

        assertThatThrownBy(() -> provider.validateToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.AUTH_401_TOKEN_EXPIRED);
    }

    @Test
    void tokenSignedWithDifferentSecretThrowsInvalidTokenException() {
        JwtTokenProvider issuerProvider = new JwtTokenProvider(createProperties("test-secret-key-test-secret-key-aaaa", 1800L, 1209600L, "tryna-test"));
        JwtTokenProvider validatorProvider = new JwtTokenProvider(createProperties("test-secret-key-test-secret-key-bbbb", 1800L, 1209600L, "tryna-test"));
        String token = issuerProvider.generateAccessToken(1L);

        assertThatThrownBy(() -> validatorProvider.validateToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.AUTH_401_INVALID_TOKEN);
    }

    @Test
    void malformedTokenThrowsInvalidTokenException() {
        JwtTokenProvider provider = new JwtTokenProvider(createProperties("test-secret-key-test-secret-key-7777", 1800L, 1209600L, "tryna-test"));

        assertThatThrownBy(() -> provider.validateToken("not-a-jwt-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.AUTH_401_INVALID_TOKEN);
    }

    @Test
    void tokenTypeMismatchThrowsBusinessException() {
        JwtTokenProvider provider = new JwtTokenProvider(createProperties("test-secret-key-test-secret-key-3333", 1800L, 1209600L, "tryna-test"));
        String accessToken = provider.generateAccessToken(1L);

        assertThatThrownBy(() -> provider.validateToken(accessToken, TokenType.REFRESH))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.AUTH_401_INVALID_TOKEN_TYPE);
    }

    private JwtProperties createProperties(String secret, long accessExpiration, long refreshExpiration, String issuer) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setAccessExpiration(accessExpiration);
        properties.setRefreshExpiration(refreshExpiration);
        properties.setIssuer(issuer);
        return properties;
    }
}
