-- 통합 분석 1번에 이용 기록이 2줄 남던 문제(#57): 같은 사용자·같은 traceId로 UNIFIED_ANALYSIS가 있는 LOAN_CALCULATION 삭제
-- traceId가 없는 기록은 짝을 확인할 수 없어 그대로 둠
-- MySQL은 삭제 대상 테이블을 하위 쿼리에서 바로 읽을 수 없어서 파생 테이블(duplicated)로 한 번 감쌈
delete from user_activity_logs
where id in (
    select id from (
        select a.id
        from user_activity_logs a
        join user_activity_logs b
          on b.user_id = a.user_id
         and b.trace_id = a.trace_id
         and b.action = 'UNIFIED_ANALYSIS'
        where a.action = 'LOAN_CALCULATION'
    ) duplicated
);

-- 없는 사용자 id로는 저장하지 못하게 하고, 사용자를 삭제하면 계산 기록·이용 기록도 함께 삭제 (#32)
alter table loan_simulations
    add constraint fk_loan_sim_user foreign key (user_id) references users (id) on delete cascade;
alter table user_activity_logs
    add constraint fk_activity_user foreign key (user_id) references users (id) on delete cascade;
