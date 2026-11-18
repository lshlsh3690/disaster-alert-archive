package com.disaster.alert.alertapi.domain.event.dashboard;

import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.Ratio;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVerdict;

import java.util.List;

/**
 * 불일치 쌍 사람 라벨의 순수 판정·집계(JPA·Spring·disasteralert 패키지 import 없음).
 * "누가 틀렸는가", 판사 버전별 어려운 쌍 성적 규칙을 이 클래스 한 곳에 둔다.
 * service 는 저장소에서 읽은 값을 아래 record 로 바꿔 넘기기만 한다.
 *
 * <p>Red 단계 뼈대 - 판정·집계 로직은 없고, 틀린 기본값(null·빈 목록)만 돌려준다.
 */
public final class DisagreementGrading {

    private DisagreementGrading() {
    }

    /** 사람 라벨을 정답으로 봤을 때 틀린 쪽. */
    public enum WrongSide {
        JUDGE, SYSTEM
    }

    /** 성적 집계 입력 — 지금 불일치인 채점 쌍 중 사람 라벨이 있는 것 하나와 그 쌍의 판사 버전. */
    public record GradedPair(String judgeVersion, Label label, JudgeVerdict judgeVerdict, boolean systemSameEvent) {
    }

    /** 판사 버전 하나의 어려운 쌍 성적. model 은 버전 목록에 없으면 null. unsureCount 는 분모에서 뺀 "모르겠음" 쌍 수. */
    public record VersionGrade(String version, String model, Ratio judgeRight, Ratio systemRight, int unsureCount) {
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
}
