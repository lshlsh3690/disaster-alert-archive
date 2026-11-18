package com.disaster.alert.alertapi.domain.event.dashboard;

import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.Ratio;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVerdict;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVersionCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 불일치 쌍 사람 라벨의 순수 판정·집계(JPA·Spring·disasteralert 패키지 import 없음).
 * "누가 틀렸는가", 판사 버전별 어려운 쌍 성적 규칙을 이 클래스 한 곳에 둔다.
 * service 는 저장소에서 읽은 값을 아래 record 로 바꿔 넘기기만 한다.
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

    /**
     * 누가 틀렸는지. UNSURE 면 정답이 없으므로 null.
     *
     * <p>호출되는 쌍은 판사와 시스템의 답이 서로 반대인 "불일치 쌍"이라 SAME/DIFF 라벨이면 정확히 한쪽만 틀린다.
     * 판사 맞음은 판사 판정이 라벨과 같은 값(SAME/DIFF)일 때만이다 — 판사 UNSURE·FORMAT_FAILURE 는 SAME/DIFF 가 아니라
     * 항상 "틀림"으로 읽히지만, 그런 쌍은 애초에 불일치 쌍이 될 수 없어 이 경로에 들어오지 않는다.
     * 불일치 쌍이 아닌 입력이 들어오면 판사가 틀렸을 때 JUDGE 를 먼저 돌려주고, 판사가 맞고 시스템이 틀렸을 때만 SYSTEM,
     * 둘 다 맞으면 틀린 쪽이 없으니 null 이다(둘 다 틀린 경우는 JUDGE).
     */
    public static WrongSide wrongSide(Label label, JudgeVerdict judgeVerdict, boolean systemSameEvent) {
        // 1) 모르겠음은 정답이 없어 누구도 틀렸다고 하지 않는다
        if (label == Label.UNSURE) {
            return null;
        }
        // 2) 판사가 틀렸으면 먼저 JUDGE — 시스템 값은 보지 않는다
        if (!isJudgeRight(label, judgeVerdict)) {
            return WrongSide.JUDGE;
        }
        // 3) 판사가 맞았을 때만 시스템을 본다. 시스템도 맞으면 틀린 쪽이 없다
        return isSystemRight(label, systemSameEvent) ? null : WrongSide.SYSTEM;
    }

    /**
     * 판사 버전별 어려운 쌍 성적(버전 목록 순서, 최신 먼저).
     *
     * <p>분모는 SAME/DIFF 라벨 쌍이고 모르겠음은 뺀 뒤 {@code unsureCount} 로만 남긴다 — 정답 없는 쌍을 맞음·틀림에 넣으면
     * 비율이 왜곡된다. 쌍이 하나도 없는 버전은 줄을 만들지 않는다. 버전 목록에 없는 버전(옛 기록)은 목록 버전 뒤에 이름순으로 붙고
     * model 은 null 이다.
     *
     * <p>입력은 지금 불일치인 쌍만이라 판사 판정은 늘 SAME/DIFF 이고 판사 맞음 + 시스템 맞음 = 분모다. 판사 UNSURE·FORMAT_FAILURE
     * 쌍이 섞여 들어오면 분모에는 들어가고 판사 맞음에서는 빠져 그 합이 분모보다 작아진다 — service 가 불일치 쌍만 넘겨야 한다.
     */
    public static List<VersionGrade> gradeByVersion(List<GradedPair> pairs) {
        // 1) 버전별로 모은다 — 쌍 입력 순서와 상관없이 아래에서 순서를 다시 정한다
        Map<String, List<GradedPair>> byVersion = new LinkedHashMap<>();
        for (GradedPair pair : pairs) {
            byVersion.computeIfAbsent(pair.judgeVersion(), v -> new ArrayList<>()).add(pair);
        }
        // 2) 목록 순서(최신 먼저)대로 먼저 꺼내고, 목록에 없는 버전은 뒤에 이름순으로 붙인다
        List<String> order = new ArrayList<>();
        for (JudgeVersionCatalog.Entry entry : JudgeVersionCatalog.all()) {
            if (byVersion.containsKey(entry.version())) {
                order.add(entry.version());
            }
        }
        byVersion.keySet().stream()
                .filter(v -> !order.contains(v))
                .sorted(Comparator.nullsLast(Comparator.naturalOrder()))
                .forEach(order::add);
        // 3) 버전마다 한 줄로 집계한다
        return order.stream().map(v -> gradeOne(v, byVersion.get(v))).toList();
    }

    // 버전 하나의 집계 — 모르겠음은 unsure 로만 세고 분모(total)에서 뺀다
    private static VersionGrade gradeOne(String version, List<GradedPair> pairs) {
        int total = 0;
        int judgeRight = 0;
        int systemRight = 0;
        int unsure = 0;
        for (GradedPair p : pairs) {
            if (p.label() == Label.UNSURE) {
                unsure++;
                continue;
            }
            total++;
            if (isJudgeRight(p.label(), p.judgeVerdict())) {
                judgeRight++;
            }
            if (isSystemRight(p.label(), p.systemSameEvent())) {
                systemRight++;
            }
        }
        // 목록에 없는 버전은 모델을 알 수 없어 null
        String model = JudgeVersionCatalog.find(version).map(JudgeVersionCatalog.Entry::model).orElse(null);
        return new VersionGrade(version, model,
                ClusteringDashboardMetrics.ratio(judgeRight, total),
                ClusteringDashboardMetrics.ratio(systemRight, total),
                unsure);
    }

    // 판사 맞음: 판사 판정이 라벨과 같은 판정(SAME↔SAME, DIFF↔DIFF)일 때만. UNSURE·FORMAT_FAILURE 는 맞음이 아니다
    private static boolean isJudgeRight(Label label, JudgeVerdict verdict) {
        return (label == Label.SAME && verdict == JudgeVerdict.SAME)
                || (label == Label.DIFF && verdict == JudgeVerdict.DIFF);
    }

    // 시스템 맞음: 같은 이벤트로 묶었는지(systemSameEvent)가 "라벨이 같은 사건인지"와 일치할 때
    private static boolean isSystemRight(Label label, boolean systemSameEvent) {
        return systemSameEvent == (label == Label.SAME);
    }
}
