package com.tryna.domain.reminder.service;

import com.tryna.domain.reminder.dto.AlarmStateResponse;
import com.tryna.domain.term.entity.Terms;
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
public class AlarmStateService {

    private final UserRepository userRepository;
    private final TermsRepository termsRepository;
    private final UserAgreedTermsRepository userAgreedTermsRepository;

    @Transactional
    public AlarmStateResponse toggleAlarmState(Long userId) {
        Users user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_401));

        if (!user.isAlarmState()) {
            validateAlarmTermAgreed(userId);
        }

        user.updateAlarmState(!user.isAlarmState());

        return AlarmStateResponse.from(user.isAlarmState());
    }

    private void validateAlarmTermAgreed(Long userId) {
        Terms alarmTerm = termsRepository.findLatestTermsByTypes(List.of(TermType.ALARM)).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(AlarmErrorCode.F100_ALARM_TOOGLE_400));

        if (!userAgreedTermsRepository.existsByUser_UserIdAndTerm_TermId(userId, alarmTerm.getTermId())) {
            throw new BusinessException(AlarmErrorCode.F100_ALARM_TOOGLE_400);
        }
    }
}
