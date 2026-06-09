package com.disaster.alert.alertapi.domain.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * cross-region 같은 사건 판정 LLM 서비스.
 *
 * <p>지역 이동 사건(실종자·탈출 동물)은 같은 시군구가 안 겹쳐 임베딩+지역필터로는 못 묶고,
 * 임베딩만으론 동명이인(0.897) {@literal >} 진짜 같은사람(0.834) 이라 분리 불가.
 * → LLM 이 이름·나이·키·몸무게·개체 특징을 비교해 "동일 인물/개체의 같은 사건"인지 판정한다.
 *
 * <p>공유 {@link ChatModel}(gpt-4o-mini, SSAFY GMS 프록시) 사용 — risk 모듈과 같은 빈.
 * 후보 K개를 한 프롬프트에 넣어 <b>알림 1건당 LLM 1콜</b>(비용 최소).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventLLMDecisionService {

    private final ChatModel chatModel;

    private static final Pattern NUMBER = Pattern.compile("\\d+");

    /** 후보 1건 — 후보 이벤트 id + 대표(seed) 알림 본문. */
    public record Candidate(Long eventId, String representativeMessage) {}

    /**
     * target 알림이 후보들 중 "동일 인물/개체의 같은 사건"과 매칭되는지 판정.
     *
     * @return 매칭된 후보의 0-based index, 없으면 null(보수적 — 실패·모호도 null).
     */
    public Integer pickSameIncident(String targetMessage, List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        String prompt = """
                다음은 한국 재난문자다. 실종자(사람)나 탈출한 동물처럼 '대상'이 지역을 옮겨다니며 여러 번 발송될 수 있다.

                [대상 문자]
                %s

                [후보 목록]
                %s
                [판정 기준]
                - 대상과 '동일한 인물' 또는 '동일한 개체'의 같은 사건인 후보를 고른다.
                - 사람: 이름·성별·나이·키·몸무게·인상착의가 일치해야 동일 인물. 이름이 같아도 나이/키가 다르면 다른 사람(동명이인).
                - 동물: 종·특징·탈출 지점/경위가 일치해야 동일 개체.
                - 단지 '같은 주제'(예: 일반 물놀이 안전수칙, 일반 안내)일 뿐이면 동일 사건이 아니다.

                동일 사건인 후보의 번호 하나만 출력. 없으면 NONE. 숫자 또는 NONE 외 다른 말은 출력하지 마라.
                """.formatted(oneLine(targetMessage), renderCandidates(candidates));

        return callAndParse(prompt, candidates.size(), "cross-region");
    }

    /**
     * 같은 시군구에서 발생한 일반 사고성 사건의 동일성 판정 (local borderline LLM 폴백용).
     *
     * <p>"서소문 고가 붕괴"처럼 하나의 사고가 진행되는 동안 여러 기관이 서로 다른 측면
     * (도로 통제·열차 운행 중지·복구 안내)을 다른 본문으로 발송해 임베딩 유사도가 임계 미만으로
     * 떨어지는 경우를 묶기 위함. {@link #pickSameIncident}(인물·개체 신원)와 달리 <b>같은 원인
     * 사고</b>인지를 본다. 폭염·호우 같은 기상특보 반복 발령을 잘못 묶지 않도록, 호출 측에서
     * 사고성 유형만 게이트한다(이 메서드 자체는 유형을 모른다).
     *
     * @return 동일 사고인 후보의 0-based index, 없으면 null(보수적 — 실패·모호도 null).
     */
    public Integer pickSameGeneralIncident(String targetMessage, List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        String prompt = """
                다음은 한국 재난문자다. 하나의 재난·사고 사건이 진행되는 동안 여러 기관이 서로 다른 측면(도로 통제, 열차 운행 중지, 복구 안내 등)을 여러 번 발송할 수 있다.

                [대상 문자]
                %s

                [후보 목록]
                %s
                [판정 기준]
                - 대상 문자와 '동일한 하나의 사건'(같은 장소·시설에서 발생한 같은 사고/재난)을 다루는 후보를 고른다.
                - 안내하는 세부 내용(도로 통제 / 열차 차질 / 복구 일정)이 달라도, 원인이 같은 사건이면 동일 사건이다.
                - 단지 같은 '유형'일 뿐(예: 일반 폭염 안전수칙, 일반 물놀이 주의)이고 특정한 같은 사건을 가리키지 않으면 동일 사건이 아니다.

                동일 사건인 후보의 번호 하나만 출력. 없으면 NONE. 숫자 또는 NONE 외 다른 말은 출력하지 마라.
                """.formatted(oneLine(targetMessage), renderCandidates(candidates));

        return callAndParse(prompt, candidates.size(), "llm-fallback");
    }

    /** 후보 목록을 "1) ...\n2) ...\n" 형태로 렌더 (LLM 프롬프트 삽입용). */
    private String renderCandidates(List<Candidate> candidates) {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            list.append(i + 1).append(") ").append(oneLine(candidates.get(i).representativeMessage())).append('\n');
        }
        return list.toString();
    }

    /**
     * LLM 호출 + 응답 파싱 공통 로직. 실패·모호·범위밖은 모두 null(보수적).
     *
     * @param size 후보 수 (응답 번호 범위 검증용)
     * @param tag  로그 태그 (cross-region / llm-fallback)
     * @return 매칭 후보 0-based index, 없으면 null
     */
    private Integer callAndParse(String prompt, int size, String tag) {
        String answer;
        try {
            answer = chatModel.call(prompt);
        } catch (Exception e) {
            log.warn("{} LLM 판정 실패 — NONE 처리: {}", tag, e.getMessage());
            return null;
        }
        if (answer == null) {
            return null;
        }
        String a = answer.trim();
        if (a.toUpperCase().contains("NONE")) {
            return null;
        }
        Matcher m = NUMBER.matcher(a);
        if (!m.find()) {
            return null;
        }
        int picked = Integer.parseInt(m.group());
        if (picked < 1 || picked > size) {
            log.warn("{} LLM 응답 범위 밖({}) — NONE 처리. 응답='{}'", tag, picked, a);
            return null;
        }
        return picked - 1;
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }
}
