package com.tryna.global.security.jwt;

import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = resolveToken(request);

            // 토큰이 존재하고, ACCESS 토큰으로서 유효한지 검증
            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt, TokenType.ACCESS)) {
                Long userId = jwtTokenProvider.getUserId(jwt);

                // Spring Security Context에 인증 정보 저장 (권한은 현재 단순화하여 빈 리스트 처리)
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, Collections.emptyList()
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (BusinessException e) {
            // 토큰 만료 등 비즈니스 예외 발생 시 SecurityContext를 비우고 넘어감
            // 이후 CustomEntryPoint에서 401 응답 처리됨
            log.warn("JWT Authentication failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            request.setAttribute("exception", e.getErrorCode());
        } catch (Exception e) {
            log.error("JWT Filter error: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            request.setAttribute("exception", AuthErrorCode.AUTH_401_INVALID_TOKEN);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
