ALTER TABLE point_histories
    ADD COLUMN overdue_record_id BIGINT NULL
        COMMENT '연체 정산 관련 history 인 경우 OverdueRecord ID';
