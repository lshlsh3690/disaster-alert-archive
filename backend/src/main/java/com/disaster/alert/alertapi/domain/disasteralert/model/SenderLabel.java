package com.disaster.alert.alertapi.domain.disasteralert.model;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 재난문자 본문에서 발신 기관 표기(대괄호 안 문자열)를 뽑는 순수 함수(헌법 VI).
 *
 * <p>클러스터링 일일 채점 판사 입력({@code AlertPlaceFacts})과 기록관 발신 기관 보관(specs/007 R11)이 같은 규칙을 쓴다.
 * <b>규칙을 바꾸면 판사 입력이 바뀌어 일일 채점 점수가 이전과 비교되지 않는다</b> — 바꾸기 전에 그 영향부터 확인할 것.
 *
 * <p>표기는 가공하지 않고 대괄호 안 문자열 그대로 돌려준다(공백 포함). 이름 정규화("서울시"↔"서울특별시")는 여기서 하지 않는다.
 */
public final class SenderLabel {

    // 발신 기관 표시는 본문 맨 끝(뒤 공백 허용)이 기본 형태다(평가 셋 544건 중 517건). 본문 중간의 대괄호는 기관이 아니므로 보지 않는다.
    private static final Pattern SENDER_AT_END = Pattern.compile("\\[([^\\[\\]]+)]\\s*$");

    // 끝에 없을 때만 보는 맨 앞 형태("[세종시청] 오늘 …", "[서울경찰청]용산구에서 …" — 27건). 괄호 뒤 공백은 있어도 없어도 된다.
    // 끝을 먼저 보는 이유: 끝 표시가 있던 기존 문자의 결과를 바꾸지 않기 위해서다(둘 다 있는 문자는 평가 셋에 없다).
    private static final Pattern SENDER_AT_START = Pattern.compile("^\\s*\\[([^\\[\\]]+)]");

    private SenderLabel() {
    }

    /** 발신 기관 표기: 끝 대괄호 우선, 없으면 맨 앞 대괄호. 둘 다 없으면 비어 있다. */
    public static Optional<String> extract(String body) {
        Matcher atEnd = SENDER_AT_END.matcher(body);
        if (atEnd.find()) {
            return Optional.of(atEnd.group(1));
        }
        Matcher atStart = SENDER_AT_START.matcher(body);
        return atStart.find() ? Optional.of(atStart.group(1)) : Optional.empty();
    }
}
