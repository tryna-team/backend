-- 기존 CHECK 제약을 먼저 제거
ALTER TABLE labels
DROP CONSTRAINT IF EXISTS ck_labels_type;

-- 기존 DEFAULT 타입 데이터를 USER 타입으로 변환
UPDATE labels
SET label_type = 'USER'
WHERE label_type = 'DEFAULT';

-- USER / EXTERNAL_CALENDAR 두 타입만 허용하도록 CHECK 제약 재생성
ALTER TABLE labels
    ADD CONSTRAINT ck_labels_type
        CHECK (
            (
                label_type = 'USER'
                    AND external_calendar_id IS NULL
                )
                OR
            (
                label_type = 'EXTERNAL_CALENDAR'
                    AND external_calendar_id IS NOT NULL
                    AND is_default = FALSE
                )
            );