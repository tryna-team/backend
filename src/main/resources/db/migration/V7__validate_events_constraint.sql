-- V6: V4에서 NOT VALID로 추가했던 제약조건들을 별도 마이그레이션 트랜잭션에서 안전하게 검증
ALTER TABLE events VALIDATE CONSTRAINT fk_events_external_calendar;
ALTER TABLE user_events VALIDATE CONSTRAINT fk_user_events_event;
ALTER TABLE events VALIDATE CONSTRAINT ck_events_external_required;