package com.tryna.domain.auth.service;

import com.tryna.domain.auth.enums.Provider;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OAuthClientProvider {

    // 스프링이 OAuthClient를 구현한 모든 Bean(Google, Dummy)을 List에 담아서 주입해 줌
    private final List<OAuthClient> clients;

    public OAuthClient getClient(Provider provider) {
        List<OAuthClient> matchedClients = clients.stream()
                .filter(client -> client.isSupported(provider))
                .collect(Collectors.toList());

        if (matchedClients.isEmpty()) {
            throw new BusinessException(AuthErrorCode.A105_AUTH_SESSION_400);
        }
        if (matchedClients.size() > 1) {
            throw new IllegalStateException("Multiple OAuth clients found for provider: " + provider);
        }

        return matchedClients.get(0);
    }
}