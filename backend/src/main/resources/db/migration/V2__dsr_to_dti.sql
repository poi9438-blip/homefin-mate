-- DSR → DTI 전환: 기존 기록은 dsr 값을 dti로 옮기고 계산 기준을 NEW_LOAN_ONLY로 표시

alter table loan_simulations add column dti decimal(5,4);
alter table loan_simulations add column dti_basis varchar(30);

update loan_simulations set dti = dsr, dti_basis = 'NEW_LOAN_ONLY';

alter table loan_simulations drop column dsr;
