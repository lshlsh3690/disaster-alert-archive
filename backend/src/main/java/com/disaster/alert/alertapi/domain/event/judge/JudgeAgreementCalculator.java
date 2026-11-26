package com.disaster.alert.alertapi.domain.event.judge;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 판사 일치율을 "쌍별 판사 판정 x 현재 사람 라벨"로 계산한다.
 *
 * <p>판정은 측정 시점에 쌍마다 저장해 두고, 사람 라벨은 계산할 때의 현재 값을 쓴다. 그래서 라벨을 고치면 같은 판정으로
 * 일치율을 다시 계산할 수 있다. JPA·Spring 을 쓰지 않는 순수 클래스다(헌법 VI).
 *
 * <p><b>왜 판정은 고정하고 라벨만 현재 값을 쓰나</b>(specs/014 research R2·R4):
 * <ul>
 *   <li><b>판사 판정은 사람 라벨과 무관하다.</b> 판사는 두 문자의 시각·유형·지역·본문과 규칙으로 계산한 사실만 받고
 *       사람 라벨은 받지 않는다({@link ClusteringJudge#buildPrompt}). 그래서 라벨이 바뀌어도 저장한 판정은 그대로 유효하다.</li>
 *   <li><b>사람 라벨은 바뀐다.</b> 272쌍 라벨은 관리자 화면에서 고치는 데이터다(2026-11-23 DB 이관). 실제로 2026-11-17 에
 *       측정 뒤 라벨을 두 번 재확인해, 코드 상수로 적어 둔 v1 일치율을 손으로 다시 세어 고쳤다.</li>
 *   <li><b>판사를 다시 부르는 것은 답이 아니다.</b> 유료이고, 온도 0 이어도 한두 쌍은 답이 달라진다(v2 225쌍 약 $0.05).
 *       다시 재면 "라벨이 바뀐 효과"와 "판사 답이 흔들린 효과"가 섞여, 일치율이 왜 달라졌는지 가를 수 없다.</li>
 *   <li><b>일치율 숫자를 저장하지 않는 이유</b>: 숫자를 저장하면 라벨을 고칠 때마다 다시 세어 덮어써야 하고, 빠뜨리면 지금의
 *       상수처럼 라벨과 어긋난다. 측정 당시 값이 필요하면 그날 일일 채점 기록({@code clustering_daily_score.agreement_*})에
 *       채점 순간의 값이 복사돼 남는다.</li>
 * </ul>
 */
public final class JudgeAgreementCalculator {

    private JudgeAgreementCalculator() {
    }

    /** 측정의 쌍 하나에 판사가 낸 판정. 알림 순서는 상관없다. */
    public record PairVerdict(long alertA, long alertB, RecordedVerdict verdict) {
    }

    /** 현재 사람 라벨 하나. 알림 순서는 상관없다(evaluation_pair_label 은 원래 순서로 저장됨). */
    public record PairLabel(long alertA, long alertB, LabelJson.Label label) {
    }

    public record Result(int sameNum, int sameDen, int diffNum, int diffDen,
                         int judgeUnsure, int formatFailures, int callFailures,
                         int unmeasuredPairs, int unlabeledVerdicts) {
    }

    /** 순서와 상관없이 같은 쌍을 같은 키로 만든다. 판정·라벨이 알림 순서를 서로 다르게 저장해 둘 수 있어서다. */
    private record PairKey(long low, long high) {
        static PairKey of(long a, long b) {
            return new PairKey(Math.min(a, b), Math.max(a, b));
        }
    }

    /**
     * 저장된 판정과 현재 사람 라벨을 쌍별로 맞춰 일치율의 분자·분모와 분모 밖 건수를 센다.
     *
     * <p>호출 맥락: 관리자 화면이 일치율을 보여 줄 때마다 부른다. 판사를 다시 부르지 않으므로 라벨을 고치면 바로 갱신된다.
     * 분모 0 이면 비율은 호출한 쪽이 "-" 로 처리한다(여기서는 건수만 돌려준다).
     */
    public static Result calculate(List<PairVerdict> verdicts, List<PairLabel> labels) {
        // 1) 판정을 (작은 id, 큰 id) 키로 모은다 — 라벨 쪽에서 순서와 무관하게 찾기 위해서다.
        Map<PairKey, RecordedVerdict> verdictByPair = new HashMap<>();
        for (PairVerdict v : verdicts) {
            verdictByPair.put(PairKey.of(v.alertA(), v.alertB()), v.verdict());
        }

        int sameNum = 0, sameDen = 0, diffNum = 0, diffDen = 0;
        int judgeUnsure = 0, formatFailures = 0, callFailures = 0, unmeasuredPairs = 0;
        int labeledAndVerdicted = 0;

        // 2) 라벨이 SAME/DIFF 인 쌍만 훑는다 — UNSURE 라벨은 정답이 없어 일치 여부를 말할 수 없다.
        for (PairLabel l : labels) {
            if (l.label() != LabelJson.Label.SAME && l.label() != LabelJson.Label.DIFF) {
                continue;
            }
            RecordedVerdict verdict = verdictByPair.get(PairKey.of(l.alertA(), l.alertB()));
            // 3) 판정이 없는 쌍은 "틀림"이 아니라 "측정 안 함"이다 — 분모에 넣으면 일치율이 부당하게 낮아진다.
            if (verdict == null) {
                unmeasuredPairs++;
                continue;
            }
            labeledAndVerdicted++;
            // 4) 판사가 답을 못 낸 쌍은 분모 밖에서 원인별로 센다 — "모른다"와 "API 장애"를 구분하려는 것이다.
            if (verdict == RecordedVerdict.UNSURE) {
                judgeUnsure++;
            } else if (verdict == RecordedVerdict.FORMAT_FAILURE) {
                formatFailures++;
            } else if (verdict == RecordedVerdict.CALL_FAILURE) {
                callFailures++;
            } else if (l.label() == LabelJson.Label.SAME) {
                // 5) 답을 낸 쌍만 분모에 넣고, 라벨과 같은 값을 냈으면 분자에도 넣는다.
                sameDen++;
                if (verdict == RecordedVerdict.SAME) {
                    sameNum++;
                }
            } else {
                diffDen++;
                if (verdict == RecordedVerdict.DIFF) {
                    diffNum++;
                }
            }
        }

        // 6) 판정은 있는데 계산에 쓰이지 않은 쌍(라벨 없음·UNSURE 라벨)을 센다 — 판정 총수에서 쓰인 쌍을 뺀 값이다.
        int unlabeledVerdicts = verdictByPair.size() - labeledAndVerdicted;
        return new Result(sameNum, sameDen, diffNum, diffDen,
                judgeUnsure, formatFailures, callFailures, unmeasuredPairs, unlabeledVerdicts);
    }
}
