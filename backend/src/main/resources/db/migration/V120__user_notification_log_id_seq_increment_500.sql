-- UserNotificationLog 의 id 를 IDENTITY 에서 SEQUENCE(allocationSize=500) 로 바꿨다.
-- IDENTITY 는 INSERT 마다 키를 DB 에서 받아야 해 JDBC 배치로 못 묶이기 때문이다.
-- Hibernate pooled 옵티마이저는 시퀀스 INCREMENT 가 allocationSize 와 같다고 가정하고,
-- ddl-auto=validate 도 둘이 다르면 부팅을 거부하므로 시퀀스 쪽을 500 으로 맞춘다.
-- 기존 BIGSERIAL 이 만든 시퀀스를 그대로 재사용하므로 setval 이 필요 없다(현재 값 이후부터 이어진다).
-- 옛 코드(IDENTITY, nextval 1건씩)로 롤백해도 새 값은 항상 기존 최대 id 보다 크므로 id 가 충돌하지 않는다.
ALTER SEQUENCE user_notification_log_id_seq INCREMENT BY 500;
