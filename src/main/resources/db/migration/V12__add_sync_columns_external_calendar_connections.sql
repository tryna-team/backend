-- external_calendar_connections 테이블에 last_synced_at 및 last_sync_status 컬럼 추가
-- idempotent 변경: 이미 컬럼이 있어도 실패하지 않도록 IF NOT EXISTS 사용
ALTER TABLE external_calendar_connections
  ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP NULL;

ALTER TABLE external_calendar_connections
  ADD COLUMN IF NOT EXISTS last_sync_status VARCHAR(50) NULL;

-- 선택 사항: 동기화 상태(last_sync_status) 빠른 조회를 위한 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_external_calendar_connections_last_sync_status ON external_calendar_connections(last_sync_status);
