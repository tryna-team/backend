-- V13: action_items 테이블의 부모 일정(events) 외래키에 ON DELETE CASCADE 속성 추가
-- 외부 캘린더 연동 해제 시, 삭제되는 일정에 매달린 action_items가 무결성 에러 없이 함께 안전하게 연쇄 삭제되도록 처리

-- 1. 기존 외래키 제약조건 안전하게 제거
ALTER TABLE action_items
    DROP CONSTRAINT IF EXISTS fk_action_items_parent_event;

-- 2. ON DELETE CASCADE 옵션을 포함하여 외래키 제약조건 재생성
ALTER TABLE action_items
    ADD CONSTRAINT fk_action_items_parent_event
        FOREIGN KEY (parent_event_id)
            REFERENCES events (event_id)
            ON DELETE CASCADE;