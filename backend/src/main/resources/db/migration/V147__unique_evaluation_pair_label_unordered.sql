-- 평가 셋 라벨에서 같은 쌍이 순서만 뒤집혀 두 번 들어가지 못하게 한다.
--
-- 기존 UNIQUE (dataset, alert_a_id, alert_b_id)(V130, V144 에서 이름만 바꿈)는 (10,20) 과 (20,10) 을 다른 행으로 본다.
-- 두 행이 함께 들어가면 판사 일치율 계산(JudgeAgreementCalculator, specs/014)이 판정 하나를 두 라벨에 대응시켜 같은 쌍을
-- 두 번 센다 — 분자·분모가 부풀고 unlabeledVerdicts 가 음수가 된다(PR #360 CodeRabbit 지적).
--
-- 계산기에서 걸러 내지 않고 여기서 막는 이유: 앱에는 라벨을 새로 넣는 경로가 없고(관리자 화면은 기존 라벨 수정만) 라벨은 SQL 덤프로만
-- 들어온다. 그 유일한 입구에서 거부해야 잘못된 덤프가 조용히 섞이지 않는다. 계산기가 한쪽을 고르게 하면 두 행의 라벨이 서로 다를 때
-- 어느 쪽이 정답인지를 코드가 임의로 정하게 된다.
--
-- 2026-11-26 운영 272쌍·로컬 사본에 역방향 중복이 없음을 확인했다 — 이 색인은 기존 데이터에서 실패하지 않는다.
CREATE UNIQUE INDEX uq_evaluation_pair_label_unordered
    ON evaluation_pair_label (dataset, LEAST(alert_a_id, alert_b_id), GREATEST(alert_a_id, alert_b_id));
