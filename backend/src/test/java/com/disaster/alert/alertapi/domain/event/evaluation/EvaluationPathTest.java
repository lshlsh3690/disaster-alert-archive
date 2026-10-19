package com.disaster.alert.alertapi.domain.event.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알림이 클러스터링에서 어느 경로를 탔는지(KEY/BROADCAST/EMBEDDING) 판별하는 순수 로직 테스트.
 * 정확한 토큰 → enum 같은 자명한 케이스는 빼고 경계·우선순위만 고정한다.
 */
class EvaluationPathTest {

    // EventClusteringService 의 기본 설정과 같은 유형 목록.
    private static final Set<String> REGIONAL = Set.of("산불", "산사태", "홍수");
    private static final Set<String> GLOBAL = Set.of("태풍");
    private static final Set<String> EPISODIC = Set.of("폭염", "한파", "대설", "강풍", "가축질병");

    private static EvaluationPath path(String type, String message, int sigunguCount) {
        return EvaluationPath.of(type, message, sigunguCount, REGIONAL, GLOBAL, EPISODIC);
    }

    /**
     * 에피소드형은 시군구가 1개면 유형별 키로 병합(KEY), 2개 이상이면 광역 이벤트(BROADCAST)로 간다.
     * 이 경계를 틀리면 다지역 폭염 쌍의 원인이 엉뚱하게 "검색 실패"로 찍힌다.
     * Red: 뼈대는 항상 EMBEDDING 을 돌려줘 두 단언 모두 실패한다.
     */
    @Test
    @DisplayName("에피소드형(폭염)은 시군구 1개면 KEY, 2개 이상이면 BROADCAST")
    void episodicSplitsBySigunguCount() {
        // 시군구 1개 -> 키 병합 경로
        assertThat(path("폭염", "폭염주의보 발효", 1)).isEqualTo(EvaluationPath.KEY);
        // 시군구 2개 -> 다지역이라 광역 경로 (경계: 1과 2 사이)
        assertThat(path("폭염", "폭염주의보 발효", 2)).isEqualTo(EvaluationPath.BROADCAST);
    }

    /**
     * 시군구가 10개를 넘으면(max-region-span 초과) 유형과 무관하게 광역이다. 지역 한정(산불)은 평소 KEY 지만
     * 11개부터는 광역이 먼저 적용돼야 한다(우선순위: 광역 > 유형 키).
     * Red: 뼈대가 EMBEDDING 을 돌려줘 둘 다 실패한다.
     */
    @Test
    @DisplayName("지역 한정(산불)은 시군구 10개까지 KEY, 11개부터 BROADCAST")
    void regionalTypeBecomesBroadcastOverSpanLimit() {
        // 10개는 한도 이내 -> 여전히 키 병합
        assertThat(path("산불", "산불 발생", 10)).isEqualTo(EvaluationPath.KEY);
        // 11개는 한도 초과 -> 유형과 무관하게 광역
        assertThat(path("산불", "산불 발생", 11)).isEqualTo(EvaluationPath.BROADCAST);
    }

    /**
     * 유형 '기타' 이면서 인물 수배 문구(이름씨+나이)면 실종자 신원 키로 병합되므로 KEY.
     * 다만 광역 판정이 먼저라 시군구 11개면 BROADCAST. 평범한 호우는 EMBEDDING 이다.
     * Red: 실종 KEY 와 우선순위 BROADCAST 단언은 실패하고, 호우 EMBEDDING 단언은 뼈대와 같아 우연히 통과(가드).
     */
    @Test
    @DisplayName("기타 + 인물 수배 문구는 KEY(광역이 먼저), 평범한 호우는 EMBEDDING")
    void missingPersonIsKeyButBroadcastWins() {
        String missing = "홍길동씨(남,45세) 키 170cm 실종, 발견 시 신고 바랍니다";
        assertThat(path("기타", missing, 1)).isEqualTo(EvaluationPath.KEY);
        // 우선순위: 시군구 11개 초과가 실종 판별보다 먼저
        assertThat(path("기타", missing, 11)).isEqualTo(EvaluationPath.BROADCAST);
        // 인물이 아닌 일반 유형은 임베딩 경로
        assertThat(path("호우", "호우주의보 발효", 1)).isEqualTo(EvaluationPath.EMBEDDING);
    }
}
