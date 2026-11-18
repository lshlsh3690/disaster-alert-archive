package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AssignmentReader.AlertSnapshot;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.Agreement;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVerdict;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 판사 일치율 측정 결과를 마크다운 리포트로 만든다(순수 문자열 조립, DB·판사 호출 없음).
 * 사용자가 숫자만이 아니라 "판사가 틀린 쌍"을 눈으로 보고 프롬프트를 고칠지 정하도록 쌍 목록을 함께 낸다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class JudgeAgreementReport {

    // ClusteringJudge.MODEL(gpt-4o-mini) 단가(USD / 100만 토큰). 모델·단가가 바뀌면 같이 고친다 —
    // 2026-11-18 모델을 바꾸고 첫 측정 때 이 값이 gpt-4o 단가로 남아 리포트 비용이 16배로 찍힌 적이 있다.
    private static final double INPUT_USD_PER_MILLION = 0.15;
    private static final double OUTPUT_USD_PER_MILLION = 0.60;
    private static final int BODY_EXCERPT_LENGTH = 100;
    private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 쌍 하나의 측정 기록 - 목록 절에 보여 줄 두 알림과 사람 라벨·판사 판정. */
    record PairRecord(Label human, JudgeVerdict verdict, String rawResponse, AlertSnapshot first, AlertSnapshot second) {
    }

    /** 판사 호출이 예외로 끝난 쌍(형식 실패와 별개). */
    record CallFailure(long alertA, long alertB, String reason) {
    }

    static String format(String datasetName, int totalPairs, int excludedHumanUnsure, JudgeAgreementResult result,
                         List<PairRecord> records, List<CallFailure> callFailures) {
        StringBuilder sb = new StringBuilder();
        // 1) 머리: 어떤 프롬프트·모델로 잰 값인지(버전마다 일치율을 따로 남기므로 반드시 적는다)
        sb.append("# 판사 일치율 - ").append(datasetName).append('\n');
        sb.append("- 판사 프롬프트 버전 ").append(ClusteringJudge.PROMPT_VERSION)
                .append(" · 모델 ").append(ClusteringJudge.MODEL).append('\n');
        sb.append("- 대상 쌍 ").append(totalPairs - excludedHumanUnsure).append("쌍(전체 ").append(totalPairs)
                .append("쌍 중 사람 라벨 UNSURE ").append(excludedHumanUnsure).append("쌍 제외)\n\n");

        // 2) 일치율: 분모는 판사가 SAME/DIFF 로 답한 쌍뿐이라, 빠진 쌍 수를 바로 아래에 같이 보인다
        sb.append("## 일치율\n");
        Agreement a = result.agreement();
        sb.append("- 같은 사건 쌍: ").append(rate(a.sameNum(), a.sameDen())).append('\n');
        sb.append("- 다른 사건 쌍: ").append(rate(a.diffNum(), a.diffDen())).append('\n');
        sb.append("- 분모에서 빠진 쌍: 판사 UNSURE ").append(result.judgeUnsure())
                .append(" · 형식 실패 ").append(result.formatFailures())
                .append(" · 호출 실패 ").append(callFailures.size()).append("(호출 실패는 교차표·토큰에도 없다)\n\n");

        appendCrossTab(sb, result);
        appendCost(sb, result);
        appendDisagreements(sb, records);
        appendUnanswered(sb, records);
        appendCallFailures(sb, callFailures);
        return sb.toString();
    }

    // 행: 사람 라벨, 열: 판사 판정. 형식 실패는 UNSURE 와 다른 열이다
    private static void appendCrossTab(StringBuilder sb, JudgeAgreementResult r) {
        sb.append("## 교차표 (행: 사람 라벨, 열: 판사 판정)\n\n");
        sb.append("| 사람＼판사 | SAME | DIFF | UNSURE | 형식 실패 |\n|---|---|---|---|---|\n");
        for (Label human : List.of(Label.SAME, Label.DIFF)) {
            sb.append("| ").append(human);
            for (JudgeVerdict v : List.of(JudgeVerdict.SAME, JudgeVerdict.DIFF, JudgeVerdict.UNSURE, JudgeVerdict.FORMAT_FAILURE)) {
                sb.append(" | ").append(r.cell(human, v));
            }
            sb.append(" |\n");
        }
        sb.append('\n');
    }

    private static void appendCost(StringBuilder sb, JudgeAgreementResult r) {
        double usd = r.promptTokens() * INPUT_USD_PER_MILLION / 1_000_000
                + r.completionTokens() * OUTPUT_USD_PER_MILLION / 1_000_000;
        sb.append("## 토큰·추정 비용\n");
        sb.append("- 프롬프트 ").append(r.promptTokens()).append(" · 완성 ").append(r.completionTokens())
                .append(" 토큰(형식 실패 포함)\n");
        // 프롬프트 캐시가 적용된 토큰도 정가로 셈한다 - 실제 청구액은 이보다 적을 수 있다
        sb.append(String.format(Locale.ROOT, "- 추정 비용(상한, 캐시 할인 미반영) $%.3f (입력 $%.2f/1M, 출력 $%.2f/1M)%n%n",
                usd, INPUT_USD_PER_MILLION, OUTPUT_USD_PER_MILLION));
    }

    // 사람과 판사가 정반대로 답한 쌍 - 사용자가 판사(프롬프트)와 사람 라벨 중 어느 쪽이 틀렸는지 눈으로 가려내는 목록
    private static void appendDisagreements(StringBuilder sb, List<PairRecord> records) {
        List<PairRecord> shown = records.stream().filter(JudgeAgreementReport::isDisagreement).toList();
        appendPairs(sb, "## 불일치 쌍 (사람 SAME-판사 DIFF, 사람 DIFF-판사 SAME)", shown);
    }

    // 판사가 UNSURE 로 답했거나 형식이 어긋난 쌍 - 일치율 분모에서 빠졌지만 왜 빠졌는지 볼 수 있어야 한다
    private static void appendUnanswered(StringBuilder sb, List<PairRecord> records) {
        List<PairRecord> shown = records.stream()
                .filter(p -> p.verdict() == JudgeVerdict.UNSURE || p.verdict() == JudgeVerdict.FORMAT_FAILURE).toList();
        appendPairs(sb, "## 판사가 답하지 못한 쌍 (UNSURE, 형식 실패)", shown);
    }

    private static void appendPairs(StringBuilder sb, String heading, List<PairRecord> shown) {
        sb.append(heading).append(" - ").append(shown.size()).append("쌍\n");
        for (PairRecord p : shown) {
            sb.append("\n#### ").append(p.first().id()).append(" ↔ ").append(p.second().id())
                    .append(" · 사람 ").append(p.human()).append(" · 판사 ").append(p.verdict()).append('\n');
            if (p.verdict() == JudgeVerdict.FORMAT_FAILURE) {
                // 무엇이 왔는지(문장부호·소문자·잘린 설명·빈 응답) 보이게 원응답을 그대로 적는다
                sb.append("- 판사 원응답: ").append(p.rawResponse() == null ? "(응답 없음)"
                        : "\"" + p.rawResponse().replace("\n", "\\n").replace("`", "'") + "\"").append('\n');
            }
            sb.append(alertLine(p.first())).append(alertLine(p.second()));
        }
        sb.append('\n');
    }

    private static boolean isDisagreement(PairRecord p) {
        return (p.human() == Label.SAME && p.verdict() == JudgeVerdict.DIFF)
                || (p.human() == Label.DIFF && p.verdict() == JudgeVerdict.SAME);
    }

    private static void appendCallFailures(StringBuilder sb, List<CallFailure> failures) {
        sb.append("## 호출 실패 쌍 - ").append(failures.size()).append("쌍\n");
        for (CallFailure f : failures) {
            sb.append("- ").append(f.alertA()).append(" ↔ ").append(f.alertB()).append(" · ").append(f.reason()).append('\n');
        }
    }

    // 알림 한 줄: id · 발송 시각 · 유형 · 본문 앞부분
    private static String alertLine(AlertSnapshot s) {
        String body = s.message().replace('\n', ' ');
        String excerpt = body.length() > BODY_EXCERPT_LENGTH ? body.substring(0, BODY_EXCERPT_LENGTH) + "…" : body;
        return "- " + s.id() + " · " + s.sentAt().format(AT) + " · " + s.type() + " · " + excerpt + "\n";
    }

    private static String rate(int num, int den) {
        return den == 0 ? "-(분모 0)" : String.format(Locale.ROOT, "%d/%d (%.1f%%)", num, den, num * 100.0 / den);
    }
}
