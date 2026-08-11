-- 알람(F100) 기능을 위한 ALARM 약관 시드, 사용자 알람 발송 상태 컬럼 추가

-- terms.term_type CHECK 제약에 ALARM 추가
-- (Hibernate ddl-auto 등으로 SERVICE/PRIVACY/LOCATION만 허용하는 제약이 생성된 환경 대응)
ALTER TABLE terms
    DROP CONSTRAINT IF EXISTS terms_term_type_check;

ALTER TABLE terms
    ADD CONSTRAINT terms_term_type_check
        CHECK (term_type IN ('SERVICE', 'PRIVACY', 'LOCATION', 'ALARM'));

-- ALARM 약관 시드 데이터 추가 (필수 약관이 아님)
INSERT INTO terms (term_type, is_required, version, created_at, updated_at)
VALUES ('ALARM', FALSE, 'v1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (term_type, version) DO NOTHING;

-- users 테이블에 알람 발송 on/off 상태 컬럼 추가
-- 기존 사용자는 ALARM 약관에 동의한 적이 없으므로 FALSE로 채운다.
ALTER TABLE users
    ADD COLUMN alarm_state BOOLEAN NOT NULL DEFAULT FALSE;
