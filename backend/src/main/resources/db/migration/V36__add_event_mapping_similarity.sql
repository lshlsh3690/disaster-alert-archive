-- event_alert_mapping 에 합류 시점 코사인 유사도 저장 (개발자용 튜닝/감사 신호).
--
-- 값 = 알림이 이벤트에 합류할 때 같은 이벤트의 최근접 멤버 알림과의 코사인 유사도 (1 - min distance).
-- borderline 머지(0.85~0.87) 점검, 유형별 유사도 분포로 임계값 튜닝 근거 확보용. 사용자 비노출.
-- NULL = 비교 없이 들어온 행: 이벤트 첫 알림(sequence_no=1), broadcast 머지(임베딩 미사용).
ALTER TABLE event_alert_mapping ADD COLUMN similarity_score DOUBLE PRECISION;
