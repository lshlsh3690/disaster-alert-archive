package com.disaster.alert.alertapi.domain.event.dashboard;

import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.Ratio;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVerdict;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 불일치 쌍 사람 라벨의 순수 판정·집계(JPA·Spring·disasteralert 패키지 import 없음).
 * "누가 틀렸는가", 판사 버전별 어려운 쌍 성적, 회귀 셋(내보내기) 항목 조립 규칙을 이 클래스 한 곳에 둔다.
 * service 는 저장소에서 읽은 값을 아래 record 로 바꿔 넘기기만 한다.
 *
 * <p>Red 단계 뼈대 - 판정·집계·조립 로직은 없고, 틀린 기본값(null·빈 목록)만 돌려준다.
 */
public final class DisagreementGrading {

    private DisagreementGrading() {
    }

    /** 사람 라벨을 정답으로 봤을 때 틀린 쪽. */
    public enum WrongSide {
        JUDGE, SYSTEM
    }

    /** 시스템 배정과 판사 판정이 어긋난 방향(채점 쌍 테이블의 disagreement 와 같은 뜻, 순수 유지를 위해 따로 둔다). */
    public enum Kind {
        /** 시스템은 같은 이벤트, 판사는 다른 사건 */
        OVER_MERGED,
        /** 시스템은 다른 이벤트, 판사는 같은 사건 */
        MISSED
    }

    /** 성적 집계 입력 — 지금 불일치인 채점 쌍 중 사람 라벨이 있는 것 하나와 그 쌍의 판사 버전. */
    public record GradedPair(String judgeVersion, Label label, JudgeVerdict judgeVerdict, boolean systemSameEvent) {
    }

    /** 판사 버전 하나의 어려운 쌍 성적. model 은 버전 목록에 없으면 null. unsureCount 는 분모에서 뺀 "모르겠음" 쌍 수. */
    public record VersionGrade(String version, String model, Ratio judgeRight, Ratio systemRight, int unsureCount) {
    }

    /** 내보내기 입력 — 라벨된 불일치 쌍 한 건. 같은 쌍이 여러 날 불일치였으면 날짜마다 한 건씩 들어온다. */
    public record LabeledDisagreement(long alertA, long alertB, Label label, LocalDate criteriaDate,
                                      LocalDate scoreDate, Kind kind, JudgeVerdict judgeVerdict) {
    }

    /** 내보내기에 쓸 알림 요약(서비스가 알림 요약 DTO 를 변환해 넘긴다). 원문이 없는 알림은 맵에 없다. */
    public record AlertInfo(long id, LocalDateTime sentAt, String type, String message) {
    }

    /** 회귀 셋 항목 하나(data-model.md "회귀 셋 항목"). q 는 1부터. */
    public record ExportItem(int q, Label label, LocalDate criteriaDate, LocalDate scoreDate, Kind kind,
                             JudgeVerdict judgeVerdict, ExportAlert a, ExportAlert b) {
    }

    /** 항목 안의 알림. at 은 "yyyy-MM-dd HH:mm" 문자열. 원문이 없으면 at·type·message 가 null. */
    public record ExportAlert(long id, String at, String type, String message) {
    }

    /** 누가 틀렸는지. UNSURE 면 null. */
    public static WrongSide wrongSide(Label label, JudgeVerdict judgeVerdict, boolean systemSameEvent) {
        // Red 단계 뼈대 — 구현은 Green 에서
        return null;
    }

    /** 판사 버전별 어려운 쌍 성적(버전 목록 순서, 최신 먼저). */
    public static List<VersionGrade> gradeByVersion(List<GradedPair> pairs) {
        // Red 단계 뼈대 — 구현은 Green 에서
        return List.of();
    }

    /** 내보내기 항목 조립 — 같은 쌍은 한 번(가장 최근 날짜), q 는 1부터. */
    public static List<ExportItem> exportItems(List<LabeledDisagreement> labeled, Map<Long, AlertInfo> alerts) {
        // Red 단계 뼈대 — 구현은 Green 에서
        return List.of();
    }
}
