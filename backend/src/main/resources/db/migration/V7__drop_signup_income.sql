-- 회원가입 때 받은 소득(encrypted_income)은 암호화해 저장만 하고 어디서도 읽지 않아 삭제 (#30)
-- 계산은 매번 화면에서 입력한 소득을 쓰므로, 쓰지 않는 개인정보는 보관하지 않음
alter table users drop column encrypted_income;
