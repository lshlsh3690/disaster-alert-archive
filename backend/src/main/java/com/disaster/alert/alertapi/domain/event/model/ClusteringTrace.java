package com.disaster.alert.alertapi.domain.event.model;

import java.util.List;

/**
 * 알림 1건의 클러스터링 판정 기록 (clustering_trace 한 줄). JPA 엔티티가 아니라 서비스가 판정 중에 아는 값을
 * 모아 {@code clusterNewAlert} 끝에서 한 번 저장하는 데이터 보관 객체다. 판단 로직은 갖지 않는다.
 *
 * <p>필드 의미와 {@code null} 규칙(후보 {@code []} 와 null 의 구분 등)은
 * docs/event-clustering/ADMIN_EVAL_DASHBOARD_DESIGN.md 4절 ① 참고.
 */
public class ClusteringTrace {

    /** 후보 이벤트와 알림 사이의 코사인 거리. JSON 으로는 {@code {"event":id,"dist":값}} 으로 저장한다. */
    public record Candidate(Long eventId, double dist) {
    }

    private final Long alertId;
    private ClusteringTracePath path;
    private List<Candidate> candidates;
    private List<Candidate> adjacentCandidates;
    private Double threshold;
    private boolean llmCalled;
    private Long llmResult;
    private ClusteringTraceResult result;
    private Long eventId;

    public ClusteringTrace(Long alertId) {
        this.alertId = alertId;
    }

    public void recordPath(ClusteringTracePath path) {
        this.path = path;
    }

    /** 같은 시군구 후보 검색 결과. 검색했는데 0건이면 빈 목록을 넘긴다(null 은 검색하지 않았다는 뜻). */
    public void recordCandidates(List<Candidate> candidates) {
        this.candidates = candidates;
    }

    /** 인접 시군구 후보 검색 결과. 실제로 조회했을 때만 호출한다. */
    public void recordAdjacentCandidates(List<Candidate> adjacentCandidates) {
        this.adjacentCandidates = adjacentCandidates;
    }

    public void recordThreshold(double threshold) {
        this.threshold = threshold;
    }

    /** LLM 폴백을 실제로 호출했음을 기록한다. {@code pickedEventId} 가 null 이면 "같은 사건 없음". */
    public void recordLlmCall(Long pickedEventId) {
        this.llmCalled = true;
        this.llmResult = pickedEventId;
    }

    public void recordResult(ClusteringTraceResult result, Long eventId) {
        this.result = result;
        this.eventId = eventId;
    }

    public Long getAlertId() {
        return alertId;
    }

    public ClusteringTracePath getPath() {
        return path;
    }

    public List<Candidate> getCandidates() {
        return candidates;
    }

    public List<Candidate> getAdjacentCandidates() {
        return adjacentCandidates;
    }

    public Double getThreshold() {
        return threshold;
    }

    public boolean isLlmCalled() {
        return llmCalled;
    }

    public Long getLlmResult() {
        return llmResult;
    }

    public ClusteringTraceResult getResult() {
        return result;
    }

    public Long getEventId() {
        return eventId;
    }
}
