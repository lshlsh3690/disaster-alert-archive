package com.disaster.alert.alertapi.domain.event.judge;

import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AgencyKind;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.AlertFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.PairFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.AlertPlaceFacts.SigunguRelation;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.JudgeAlert;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.JudgeInput;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 판사의 순수 로직 테스트 — 응답 해석과 프롬프트 조립만 본다. LLM 은 부르지 않는다(실제 호출은 일치율 측정이 그 역할이다).
 * DB·Spring 없이 도는 단위 테스트다.
 */
class ClusteringJudgeTest {

    /**
     * 응답이 정확히 "SAME" 이면 같은 사건으로 받는다 — 판사의 기본 경로.
     * Red 단계에서는 뼈대가 항상 UNSURE 를 돌려줘서 SAME 을 기대하는 이 단언에서 실패한다.
     */
    @Test
    void 응답이_정확히_SAME_이면_SAME_으로_받는다() {
        // 준비 없음 — 판사가 돌려준 문자열 그대로가 입력이다

        // 실행: 정확히 한 단어("SAME")인 응답을 해석한다
        JudgeVerdict verdict = ClusteringJudge.parseResponse("SAME");

        // 검증: 정해진 한 단어는 그 판정으로 받는다
        assertThat(verdict).isEqualTo(JudgeVerdict.SAME);
    }

    /**
     * 한 단어가 아닌 응답은 UNSURE 로 바꾸지 않고 형식 실패로 따로 센다. UNSURE 로 합치면 "판사가 모른다"와
     * "프롬프트·모델이 형식을 어긴다"가 구분되지 않아, 점수에서 빠지는 쌍이 왜 빠졌는지 알 수 없다.
     * Red 단계에서는 뼈대가 UNSURE 를 돌려줘서 FORMAT_FAILURE 를 기대하는 이 단언에서 실패한다.
     */
    @Test
    void 한_단어가_아닌_응답은_UNSURE_가_아니라_FORMAT_FAILURE_이다() {
        // 준비: 판정 단어로 시작하지만 이유가 덧붙은 응답 — 앞 단어만 떼어 SAME 으로 읽으면 안 되는 경우다
        String response = "SAME, 같은 하천이므로";

        // 실행
        JudgeVerdict verdict = ClusteringJudge.parseResponse(response);

        // 검증: UNSURE(판사가 모른다고 답함)가 아니라 FORMAT_FAILURE(형식을 못 받음)여야 한다
        assertThat(verdict).isEqualTo(JudgeVerdict.FORMAT_FAILURE);
    }

    /**
     * 프롬프트에 두 문자의 본문과 규칙으로 계산한 사실이 실제로 들어간다. 특히 시군구가 아닌 기관(철도) 문자의
     * 시군구는 틀린 값을 지어내지 않고 "모름"으로 적혀야 한다 — 틀린 시군구를 사실로 주면 판사가 그것을 믿고 틀린다.
     * Red 단계에서는 뼈대가 빈 문자열을 돌려줘서 본문·사실이 들어 있다는 단언에서 실패한다.
     */
    @Test
    void 프롬프트에_두_문자의_본문과_계산된_사실이_들어가고_시군구가_아닌_기관의_시군구는_모름이다() {
        // 준비: 첫 문자는 철도 기관 발신(시군구를 규칙으로 못 정함 → sigunguId 비어 있음)
        String railBody = "[한국철도공사] 임천교 인근 선로 침수로 열차 운행이 중지되었습니다.";
        JudgeAlert railway = new JudgeAlert(
                LocalDateTime.of(2026, 11, 10, 9, 0), "호우", List.of("충청북도 청주시"), railBody,
                new AlertFacts(Optional.of("한국철도공사"), AgencyKind.NON_SIGUNGU, Optional.empty(), 1));
        // 준비: 둘째 문자는 시군구 기관(청주시청) 발신 — 시군구가 정해진 쪽이라 "모름"과 대비된다
        String cityBody = "[청주시청] 임천교 일대 하천 범람 위험으로 접근을 금지합니다.";
        JudgeAlert city = new JudgeAlert(
                LocalDateTime.of(2026, 11, 10, 12, 0), "호우", List.of("충청북도 청주시"), cityBody,
                new AlertFacts(Optional.of("청주시청"), AgencyKind.SIGUNGU, Optional.of("43110"), 1));
        // 준비: 한쪽 시군구를 모르니 두 문자의 시군구 관계도 UNKNOWN, 같은 시설 후보로 "임천교"가 겹친다
        PairFacts pair = new PairFacts(SigunguRelation.UNKNOWN, Duration.ofHours(3), List.of("임천교"));

        // 실행: 두 문자와 사실만으로 프롬프트를 조립한다
        String prompt = ClusteringJudge.buildPrompt(new JudgeInput(railway, city, pair));

        // 검증 1: 두 문자의 본문이 판단 재료로 들어간다
        assertThat(prompt).contains(railBody).contains(cityBody);
        // 검증 2: 계산된 사실(발신 기관, 시군구 식별자, 같은 시설 후보)이 판단 재료로 넘어간다
        assertThat(prompt).contains("한국철도공사").contains("청주시청").contains("43110").contains("임천교");
        // 검증 3: 시군구가 아닌 기관의 시군구 줄은 "모름" — 지어내지 않고 LLM 이 본문으로 판단하게 넘긴다
        assertThat(prompt.lines().anyMatch(line -> line.contains("시군구") && line.contains("모름"))).isTrue();
        // 검증 4: 사실 블록은 규칙으로 계산한 값이며 판정이 아니라고 밝힌다(판사가 사실을 정답으로 오해하지 않게)
        assertThat(prompt).contains("규칙으로 계산한 값");
    }

    /**
     * 판사의 모델 상수와 버전 목록(JudgeVersionCatalog)의 같은 버전 모델이 어긋나지 않게 한다 — 모델을 바꾸고 목록을
     * 안 고치면 대시보드·리포트가 엉뚱한 모델·단가를 보여 준다. 새 버전을 재는 중이라 목록에 아직 없으면 검사하지 않는다
     * (그 동안 일일 채점은 일치율이 없어 건너뛴다).
     */
    @Test
    void 판사_모델은_버전_목록의_같은_버전_모델과_같다() {
        Optional<JudgeVersionCatalog.Entry> entry = JudgeVersionCatalog.find(ClusteringJudge.PROMPT_VERSION);
        assumeTrue(entry.isPresent(), "판사 버전이 아직 목록에 없음(측정 중)");

        assertThat(ClusteringJudge.MODEL).isEqualTo(entry.get().model());
    }

    // 뺀 케이스: SAME 외 DIFF/UNSURE 정확한 토큰 → 해당 값(입력 하나에 출력 하나가 자명), 일치율 조회(상수 조회라 값 확인뿐).
    // "이벤트 id 가 프롬프트에 안 들어간다"는 JudgeInput 에 그런 필드가 없는 타입 구조로 보장되어 별도 테스트가 필요 없다.
}
