-- V14: 필수 약관 시드 데이터 삽입 멱등성 보장 (중복 키 발생 시 무시)
INSERT INTO terms (term_type, is_required, version, created_at, updated_at)
VALUES ('SERVICE', TRUE, 'v1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (term_type, version) DO NOTHING;

INSERT INTO terms (term_type, is_required, version, created_at, updated_at)
VALUES ('PRIVACY', TRUE, 'v1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (term_type, version) DO NOTHING;