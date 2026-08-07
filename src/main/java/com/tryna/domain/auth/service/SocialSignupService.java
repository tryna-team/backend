package com.tryna.domain.auth.service;

import com.tryna.domain.auth.entity.Auths;
import com.tryna.domain.auth.enums.Provider;
import com.tryna.domain.auth.repository.AuthsRepository;
import com.tryna.domain.label.entity.Labels;
import com.tryna.domain.label.enums.LabelColor;
import com.tryna.domain.label.repository.LabelsRepository;
import com.tryna.domain.term.entity.Terms;
import com.tryna.domain.term.enums.TermType;
import com.tryna.domain.term.entity.mapping.UserAgreedTerms;
import com.tryna.domain.term.repository.TermsRepository;
import com.tryna.domain.term.repository.UserAgreedTermsRepository;
import com.tryna.domain.user.entity.UserSettings;
import com.tryna.domain.user.entity.Users;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.domain.user.repository.UserSettingsRepository;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SocialSignupService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final AuthsRepository authsRepository;
    private final TermsRepository termsRepository;
    private final UserAgreedTermsRepository userAgreedTermsRepository;
    private final LabelsRepository labelsRepository;

    /**
     * 신규 회원 가입을 독립된 트랜잭션(REQUIRES_NEW)으로 처리합니다.
     * 충돌 발생 시 이 메서드 내부의 변경 사항은 통째로 롤백됩니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Users registerNewUser(
            Provider provider,
            String socialId,
            String email,
            String oauthRefreshToken,
            String grantedScopes,
            List<TermType> agreedTermTypes
    ) {
        // 1. 필수 약관 검증
        List<TermType> types = (agreedTermTypes != null) ? agreedTermTypes : List.of();
        List<TermType> requiredTypes = termsRepository.findRequiredTermTypes();

        if (!types.containsAll(requiredTypes)) {
            throw new BusinessException(AuthErrorCode.TERMS_400);
        }

        // 2. 유저 및 설정 생성
        Users user = userRepository.save(Users.createUser());
        userSettingsRepository.save(UserSettings.createDefault(user));

        // 2-1. 신규 회원 가입 시 기본 라벨 생성
        String defaultLabelName = "기본";
        String normalizedName = defaultLabelName.toLowerCase(Locale.ROOT);

        Labels defaultLabel = Labels.createDefault(
                user,
                defaultLabelName,
                normalizedName,
                LabelColor.GREEN,
                1
        );
        labelsRepository.save(defaultLabel);

        // 3. 소셜 인증 정보 생성 (UQ 충돌 시 예외 발생 -> 이 트랜잭션 전체 롤백)
        Auths newAuth = Auths.createAuth(user, provider, socialId, email, oauthRefreshToken, grantedScopes);
        authsRepository.saveAndFlush(newAuth);

        // 4. 약관 동의 이력 저장
        if (!types.isEmpty()) {
            List<Terms> latestTerms = termsRepository.findLatestTermsByTypes(types);
            List<UserAgreedTerms> agreedTermsList = latestTerms.stream()
                    .map(term -> UserAgreedTerms.create(user, term))
                    .toList();
            userAgreedTermsRepository.saveAll(agreedTermsList);
        }

        return user;
    }
}