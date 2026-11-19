package com.disaster.alert.alertapi.domain.event.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 전국 예정 행사(수능·민방위훈련) 판정 규칙 — 순수 단위 테스트(Spring 컨텍스트 없음).
 */
class NationalScheduledEventTest {

    // 결정: 수능 문자는 "대학수학능력시험"·"수능부정행위"로 잡되, 단어 일부로 든 "수능"(지명·축제명)과
    //       유형이 기타·민방공이 아닌 문자(한파 안내 속 "수능 당일")는 잡지 않는다.
    //       잘못 걸리면 산불·한파 같은 실제 사고 문자가 수능 이벤트에 묻힌다.
    // Red: 뼈대 match 는 항상 Optional.empty() 라 첫 번째 "수능이어야 한다" 단언부터 실패한다.
    @Test
    @DisplayName("수능 문자는 수능으로 판정하고, 지명·축제명 속 수능과 한파 유형 문자는 판정하지 않는다")
    void matchesSuneungTextButNotLookalikes() {
        // 준비: 운영에서 실제로 온 수능 문자 2종과, 같은 글자가 들었지만 수능 행사가 아닌 문자 3종.
        // 시험 시작 안내(교육부, 기타 유형) — "대학수학능력시험" 전체 이름으로 잡힌다.
        String examStart = "(TEST ALERT) 잠시 후 2027학년도 대학수학능력시험이 시작됩니다. 공정한 시험운영을 위한 협조를 부탁드립니다. [교육부]";
        // 부정행위 방지 안내(사천시) — "수능" 바로 뒤에 "부정"이 붙은 꼴로 잡힌다.
        String cheating = "정부에서는 수능부정행위 방지를 위해 시험 중 위급재난문자를 발송할 예정입니다.[사천시]";
        // 지명 "수능리" — "수능" 뒤에 일·당일·시험·부정이 아니라 "리"가 와서 걸리면 안 된다.
        String placeName = "서종면 수능리 산불이 발생했습니다. 인근 주민은 대피하세요.";
        // 축제명 "연수능허대축제" — "수능" 앞뒤가 글자로 이어진 단어 일부라 걸리면 안 된다.
        String festival = "연수능허대축제 관람객은 안전에 유의해 주시기 바랍니다.";
        // 한파 유형 문자 — 본문에 "수능 당일"이 있어도 기타·민방공이 아니면 원래 기준(한파)을 따른다.
        String coldWave = "수능 당일인 익일 아침 기온이 큰 폭으로 하강하니 건강에 유의하세요.";

        // 실행 + 검증: 수능 2건만 SUNEUNG, 나머지는 빈 값(= 원래 클러스터링 경로로 간다).
        assertAll(
                () -> assertThat(NationalScheduledEvent.match("기타", examStart)).contains(NationalScheduledEvent.SUNEUNG),
                () -> assertThat(NationalScheduledEvent.match("기타", cheating)).contains(NationalScheduledEvent.SUNEUNG),
                () -> assertThat(NationalScheduledEvent.match("기타", placeName)).isEmpty(),
                () -> assertThat(NationalScheduledEvent.match("기타", festival)).isEmpty(),
                () -> assertThat(NationalScheduledEvent.match("한파", coldWave)).isEmpty()
        );
    }

    // 결정: 민방위훈련은 안내 문자(기타)와 훈련 중 경계경보 문자(민방공) 둘 다 같은 행사로 잡는다.
    //       예전(2026-08-20)에는 이 둘이 이벤트 4개로 쪼개졌다. 영문 병기("Civil Defense Drill")도 키워드다.
    // Red: 뼈대 match 가 Optional.empty() 라 두 단언 모두 실패한다.
    @Test
    @DisplayName("민방위훈련 문자는 기타·민방공 유형 모두 민방위훈련으로 판정한다")
    void matchesCivilDefenseDrillForBothTypes() {
        // 준비: 훈련 전날 안내(기타, "민방위훈련" 한글 키워드)와 훈련 중 경계경보(민방공, 영문 키워드만 있음).
        String notice = "내일 오후 2시 민방위훈련. 사이렌 울림, 대피, 긴급차량 길터주기 참여 바람.(Civil Defense Drill, August 20, 2pm) [행정안전부]";
        String alarm = "14:15 훈련 경계경보 발령. 국민 여러분께서는 안전한 곳으로 대피하십시오.(Civil Defense Drill) [행정안전부]";

        // 실행 + 검증: 두 유형 모두 같은 행사 종류로 나와야 한 이벤트로 묶인다.
        assertAll(
                () -> assertThat(NationalScheduledEvent.match("기타", notice)).contains(NationalScheduledEvent.CIVIL_DEFENSE_DRILL),
                () -> assertThat(NationalScheduledEvent.match("민방공", alarm)).contains(NationalScheduledEvent.CIVIL_DEFENSE_DRILL)
        );
    }

    // 결정: 제목이 곧 "같은 행사" 키다 — 수능·민방위훈련 모두 "{알림 연도} 행사명" 꼴(시험·훈련을 치른 해).
    //       수능을 "2027학년도"(알림 연도+1)로 쓰면 사용자가 아는 "2026 수능"과 어긋나 2026-11-19 에 시험 해로 정했다.
    //       해마다 열리는 다음 행사와는 연도가 달라 서로 병합되지 않는다.
    // Red: 현재 SUNEUNG.title 은 "2027학년도 수능"(알림 연도+1 학년도)을 돌려줘 첫 단언이 실패한다. 민방위훈련 단언은 이미 통과한다.
    @Test
    @DisplayName("제목은 수능이 '{연도} 수능', 민방위훈련이 '{연도} 민방위훈련'이다")
    void titlesCarryEventAndYear() {
        // 준비: 수능 알림은 11월(시험 해), 민방위훈련 알림은 8월. 시각은 월 경계가 아닌 임의의 날로 둔다.
        LocalDateTime suneungAlert = LocalDateTime.of(2026, 11, 18, 10, 0);
        LocalDateTime drillAlert = LocalDateTime.of(2026, 8, 19, 9, 0);

        // 실행 + 검증
        assertAll(
                () -> assertThat(NationalScheduledEvent.SUNEUNG.title(suneungAlert)).isEqualTo("2026 수능"),
                () -> assertThat(NationalScheduledEvent.CIVIL_DEFENSE_DRILL.title(drillAlert)).isEqualTo("2026 민방위훈련")
        );
    }
}
