package com.disaster.alert.alertapi.domain.risk.repository.projection;

/**
 * 확산 전파 입력 — source_score > 0 인 시군구의 자기 점수 + 유형별 확산계수.
 * getter 이름 = 네이티브 쿼리 alias.
 */
public interface RiskSourceRow {
    String getRegionCode();
    double getSourceScore();
    Long getSourceTopEventId();
    double getSpreadCoeff();
}
