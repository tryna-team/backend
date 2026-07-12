package com.tryna.domain.auth.service;

import com.tryna.domain.auth.enums.Provider;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OAuthClientProvider {

    // 스프링이 OAuthClient를 구현한 모든 Bean(Google, Dummy)을 List에 담아서 주입해 줌
    private final List<OAuthClient> clients;

    public OAuthClient getClient(Provider provider) {
        return clients.stream()
                .filter(client -> client.isSupported(provider))
                .findFirst()
                .orElseThrow(() -> new BusinessException(AuthErrorCode.A105_AUTH_SESSION_400));    }
}