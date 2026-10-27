-- 대출 신청 번호: 신청 내용과 상태는 FinFlow에 있고, HomeFin은 어느 사용자의 신청인지만 보관
create table user_applications (
    id             bigint      not null auto_increment,
    user_id        bigint      not null,
    -- FinFlow 신청 번호 (UUID, 같은 신청을 다시 보내도 한 줄만)
    application_id varchar(36) not null,
    product_code   varchar(30) not null,
    -- 신청에 쓴 계산 기록 (없을 수 있음, 계산 기록이 지워져도 신청 번호는 남김)
    simulation_id  bigint,
    trace_id       varchar(64),
    created_at     datetime(6) not null,
    primary key (id),
    constraint uk_user_applications_application_id unique (application_id),
    -- 사용자를 삭제하면 신청 번호도 함께 삭제 (FinFlow의 신청 기록은 FinFlow가 보관)
    constraint fk_user_applications_user foreign key (user_id) references users (id) on delete cascade,
    constraint fk_user_applications_simulation foreign key (simulation_id) references loan_simulations (id)
        on delete set null
);

-- 내 신청 목록 (최신순)
create index idx_user_applications_user_created on user_applications (user_id, created_at);
