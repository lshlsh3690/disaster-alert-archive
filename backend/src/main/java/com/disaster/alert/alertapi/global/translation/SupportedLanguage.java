package com.disaster.alert.alertapi.global.translation;

import java.util.Arrays;
import java.util.Optional;

/**
 * 지원하는 번역 언어 코드.
 *
 * <p>API 요청에서 받는 {@code lang} 쿼리 파라미터는 소문자(예: "en"),
 * DB에 저장되는 {@code language_code} 컬럼은 대문자(예: "EN")로 관리한다.
 *
 * <p>한국어("ko")는 번역 대상이 아니라 원본이므로 이 enum에 포함하지 않는다.
 *
 * <p>새 언어 추가 시 이 enum에만 항목을 추가하면 DeepL 호출, DB 조회, 응답 매핑이
 * 모두 일관되게 동작한다.
 */
public enum SupportedLanguage {
    EN("en"),
    JA("ja"),
    ZH("zh");

    /** API 요청/응답에서 사용하는 소문자 표기 */
    private final String code;

    SupportedLanguage(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** DB 저장 / DeepL API target_lang 으로 쓰이는 대문자 표기 */
    public String getDbCode() {
        return name();
    }

    /**
     * 요청 파라미터(예: "en", "EN", "ja")를 enum 으로 변환.
     * 한국어("ko") 또는 지원하지 않는 코드는 {@link Optional#empty()} 반환 → 번역 스킵.
     */
    public static Optional<SupportedLanguage> fromRequestParam(String param) {
        if (param == null || param.isBlank() || "ko".equalsIgnoreCase(param)) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(l -> l.code.equalsIgnoreCase(param))
                .findFirst();
    }
}
