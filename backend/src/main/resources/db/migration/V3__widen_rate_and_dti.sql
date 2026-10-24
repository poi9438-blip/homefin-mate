-- 연이율 99.9999%까지, DTI 999.9999(99,999%)까지 저장 (그 이상은 요청 검증·DtiRule 상한으로 막음)
alter table loan_simulations modify column interest_rate decimal(6,4) not null;
alter table loan_simulations modify column dti decimal(7,4);
