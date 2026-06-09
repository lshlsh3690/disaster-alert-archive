package com.disaster.alert.alertapi.domain.event.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 실종 경찰문자에서 신원(이름·나이·키) 추출 — 정형 "OOO씨(성별,N세) …NNNcm".
 *
 * <p>실종자는 지역을 옮겨다녀 같은 시군구가 안 겹치거나, 반대로 같은 시군구의 다른 실종자가
 * 템플릿 유사도(~0.9)로 섞인다. 그래서 임베딩/지역이 아니라 <b>이름+나이+키</b>로 동일인을
 * 판정한다(같은 사람=한 이벤트, 다른 사람=다른 이벤트). 클러스터링·cross-region 양쪽 공용.
 */
public final class MissingPersonIdentity {

    private static final Pattern NAME = Pattern.compile("([가-힣]{2,4})씨");
    private static final Pattern AGE = Pattern.compile("(\\d{1,3})\\s*세");
    private static final Pattern HEIGHT = Pattern.compile("(\\d{2,3})\\s*cm");

    private MissingPersonIdentity() {
    }

    /** "OOO씨" 이름. 없으면 null. */
    public static String name(String message) {
        return group(NAME, message);
    }

    /** "N세" 나이. 없으면 null. */
    public static String age(String message) {
        return group(AGE, message);
    }

    /** "NNNcm" 키. 없으면 null (이름+나이만으로 매칭). */
    public static String height(String message) {
        return group(HEIGHT, message);
    }

    /** 실종 인물 문자인가 (이름+나이 추출 가능). */
    public static boolean isPerson(String message) {
        return name(message) != null && age(message) != null;
    }

    private static String group(Pattern p, String message) {
        if (message == null) {
            return null;
        }
        Matcher m = p.matcher(message);
        return m.find() ? m.group(1) : null;
    }
}
