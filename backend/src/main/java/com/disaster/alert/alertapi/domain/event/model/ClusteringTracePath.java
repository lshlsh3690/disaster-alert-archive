package com.disaster.alert.alertapi.domain.event.model;

/**
 * 알림 1건의 클러스터링 판정이 어느 경로에서 끝났는지 (clustering_trace.path).
 *
 * <p>{@code EventClusteringService} 메서드와의 대응은 docs/event-clustering/ADMIN_EVAL_DASHBOARD_DESIGN.md 4절 ① 표 참고.
 */
public enum ClusteringTracePath {
    /** 임베딩 경로 — 후보 검색·임계값·인접 후보·LLM 폴백. */
    EMBEDDING,
    /** 지역 한정 유형(산불 등) 키 경로, 인접 시군구 산불 병합 포함. */
    REGIONAL,
    /** 에피소드형 유형(폭염 등) 시군구 1개 키 경로. */
    EPISODIC,
    /** 실종자 신원 키 경로. */
    PERSON,
    /** 동물 탈출·출몰 경로 — 종 식별된 '기타' 알림을 종 정규식 후보 + LLM 판정으로 처리. 실종자 다음 순서. */
    ANIMAL,
    /**
     * 전국 통합 유형(태풍) 키 경로 — <b>유형</b>이 기준이라 발송 시군구가 1개여도 여기로 온다. 키는 유형만.
     * {@code MergeMethod.GLOBAL_TYPE}, {@code clustering.global-types} 와 같은 이름.
     */
    GLOBAL_TYPE,
    /**
     * 광역 broadcast 경로 — <b>발송 시군구 수</b>가 기준: {@code max-region-span}(10) 초과, 또는 에피소드형 유형이
     * 시군구 2개 이상. 키는 시도+유형(시도 {@code nationwide-sido-span}(8) 이상이면 전국+유형).
     */
    BROADCAST,
    /** 임베딩 경로에 왔는데 지역 코드가 없어 항상 신규로 만든 경우. */
    NO_REGION,
    /** 비사건 또는 본문 없음으로 클러스터링에서 제외된 경우. */
    SKIPPED
}
