package com.tryna.domain.reminder.service;

import com.tryna.domain.term.entity.Terms;
import com.tryna.domain.term.entity.mapping.UserAgreedTerms;
import com.tryna.domain.term.enums.TermType;
import com.tryna.domain.term.repository.TermsRepository;
import com.tryna.domain.term.repository.UserAgreedTermsRepository;
import com.tryna.domain.user.entity.Users;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.global.exception.AlarmErrorCode;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlarmTermService {

    private final UserRepository userRepository;
    private final TermsRepository termsRepository;
    private final UserAgreedTermsRepository userAgreedTermsRepository;

    /**
     * F100: 알람 약관 동의
     * ALARM 약관 동의 이력을 저장하고 사용자의 alarm_state를 true로 변경합니다.
     *
     * @return true면 신규 동의, false면 이미 동의한 상태
     */
    @Transactional
    public boolean agreeAlarmTerm(Long userId) {
        Users user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_401));

        Terms alarmTerm = termsRepository.findLatestTermsByTypes(List.of(TermType.ALARM)).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(AlarmErrorCode.F100_ALARM_TERM_400));

        if (userAgreedTermsRepository.existsByUser_UserIdAndTerm_TermId(userId, alarmTerm.getTermId())) {
            return false;
        }

        userAgreedTermsRepository.save(UserAgreedTerms.create(user, alarmTerm));
        user.updateAlarmState(true);
        return true;
    }
}
