package com.tryna.domain.auth.enums;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PermissionAction {

    // A104 명세서 기준 외부 캘린더 연동 액션
    EXTERNAL_CALENDAR_SYNC(
            true,
            "이 기능을 사용하려면 로그인이 필요해요.\n\n로그인하면 일정과 준비 항목을 안전하게 저장하고,\n다른 기기에서도 이어서 확인할 수 있어요."
    );

    private final boolean loginRequired;
    private final String guideMessage;
}
