-- 1. 라벨 테이블 생성
CREATE TABLE labels (
                        label_id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        external_calendar_id BIGINT NULL,
                        name VARCHAR(100) NOT NULL,
                        normalized_name VARCHAR(100) NOT NULL,
                        label_type VARCHAR(30) NOT NULL DEFAULT 'USER',
                        color VARCHAR(7) NOT NULL,
                        is_default BOOLEAN NOT NULL DEFAULT FALSE,
                        is_visible BOOLEAN NOT NULL DEFAULT TRUE,
                        sort_order INT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        deleted_at TIMESTAMP NULL,

                        CONSTRAINT fk_labels_user
                            FOREIGN KEY (user_id)
                                REFERENCES users(user_id),

                        CONSTRAINT fk_labels_external_calendar
                            FOREIGN KEY (external_calendar_id)
                                REFERENCES external_calendars(external_calendar_id),

                        CONSTRAINT ck_labels_type
                            CHECK (
                                (
                                    label_type IN ('DEFAULT', 'USER')
                                        AND external_calendar_id IS NULL
                                    )
                                    OR
                                (
                                    label_type = 'EXTERNAL_CALENDAR'
                                        AND external_calendar_id IS NOT NULL
                                        AND is_default = FALSE
                                    )
                                )
);

-- 2. 조회용 인덱스
CREATE INDEX idx_labels_user_id
    ON labels (user_id);

CREATE INDEX idx_labels_external_calendar_id
    ON labels (external_calendar_id);

CREATE INDEX idx_labels_user_sort_order
    ON labels (user_id, sort_order);

-- 3. 동일 사용자 내 활성 라벨 이름 중복 방지
CREATE UNIQUE INDEX uq_labels_user_normalized_name_active
    ON labels (user_id, normalized_name)
    WHERE deleted_at IS NULL;

-- 4. 사용자별 활성 기본 라벨 최대 1개
CREATE UNIQUE INDEX uq_labels_user_default_active
    ON labels (user_id)
    WHERE is_default = TRUE
      AND deleted_at IS NULL;

-- 5. 외부 캘린더별 활성 라벨 최대 1개
CREATE UNIQUE INDEX uq_labels_external_calendar_active
    ON labels (external_calendar_id)
    WHERE external_calendar_id IS NOT NULL
      AND deleted_at IS NULL;

-- 6. 기존 사용자에게 기본 라벨 생성
INSERT INTO labels (
    user_id,
    external_calendar_id,
    name,
    normalized_name,
    label_type,
    color,
    is_default,
    is_visible,
    sort_order,
    created_at,
    updated_at
)
SELECT
    u.user_id,
    NULL,
    '기본',
    '기본',
    'DEFAULT',
    '#FF9500',
    TRUE,
    TRUE,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM users u
WHERE u.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1
    FROM labels l
    WHERE l.user_id = u.user_id
      AND l.is_default = TRUE
      AND l.deleted_at IS NULL
);

-- 7. user_events에 라벨 FK 추가
-- 일정 생성 담당 코드가 아직 label 없이 저장할 수 있으므로 우선 NULL 허용
ALTER TABLE user_events
    ADD COLUMN label_id BIGINT NULL;

ALTER TABLE user_events
    ADD CONSTRAINT fk_user_events_label
        FOREIGN KEY (label_id)
            REFERENCES labels(label_id);

CREATE INDEX idx_user_events_label_id
    ON user_events (label_id);

CREATE INDEX idx_user_events_user_label
    ON user_events (user_id, label_id);

-- 8. 기존 일정 매핑을 해당 사용자의 기본 라벨에 연결
UPDATE user_events ue
SET label_id = l.label_id
    FROM labels l
WHERE l.user_id = ue.user_id
  AND l.is_default = TRUE
  AND l.deleted_at IS NULL
  AND ue.label_id IS NULL;