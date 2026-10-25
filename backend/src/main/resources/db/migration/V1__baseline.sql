-- Flyway 도입 전 스키마 (ddl-auto: update가 만들던 그대로, Hibernate MySQLDialect로 뽑음)
-- 이미 테이블이 있는 DB는 이 파일을 실행하지 않고 버전 1로 표시만 함 (baseline-on-migrate)

create table audit_logs (
    created_at datetime(6),
    id bigint not null auto_increment,
    action varchar(255) not null,
    actor_id varchar(255),
    actor_type varchar(255) not null,
    trace_id varchar(255),
    primary key (id)
) engine=InnoDB;

create table loan_simulations (
    dsr decimal(5,4),
    interest_rate decimal(5,4) not null,
    loan_amount decimal(15,2) not null,
    monthly_payment decimal(15,2),
    term_months integer,
    total_interest decimal(15,2),
    created_at datetime(6),
    id bigint not null auto_increment,
    user_id bigint not null,
    region varchar(255),
    trace_id varchar(255),
    primary key (id)
) engine=InnoDB;

create table login_failure_logs (
    created_at datetime(6),
    id bigint not null auto_increment,
    email varchar(255) not null,
    reason varchar(255),
    trace_id varchar(255),
    primary key (id)
) engine=InnoDB;

create table user_activity_logs (
    created_at datetime(6),
    id bigint not null auto_increment,
    user_id bigint not null,
    action varchar(255) not null,
    trace_id varchar(255),
    primary key (id)
) engine=InnoDB;

create table users (
    created_at datetime(6),
    id bigint not null auto_increment,
    email varchar(255) not null,
    encrypted_income varchar(255),
    password varchar(255) not null,
    role enum ('USER','ADMIN','OPERATOR','AUDITOR') not null,
    primary key (id)
) engine=InnoDB;

create index idx_audit_created on audit_logs (created_at);
create index idx_loan_sim_user_created on loan_simulations (user_id, created_at);
create index idx_login_failure_created on login_failure_logs (created_at);
create index idx_activity_created on user_activity_logs (created_at);
alter table users add constraint UK_6dotkott2kjsp8vw4d0m25fb7 unique (email);
