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
 * <p><b>EN/JA/ZH 로 한정한다.</b> 2026-08-11 에 VI/TH 를 제거했다. 이 셋만이 법정동 명칭 시드
 * ({@code legal_district_translation})를 갖고 있어, 본문과 지역명 표기가 함께 번역되는 유일한
 * 조합이기 때문이다. 시드는 두 갈래다 — {@code V7}(EN)·{@code V15}(JA)·{@code V16}(ZH)가
 * 전국 법정동의 기본 시드이고, {@code V113}은 2026-07-01 전남광주 통합으로 신설된 {@code 12xx}
 * 코드에 대해 같은 세 언어를 옛 코드에서 파생해 채우는 보충 마이그레이션이다. VI/TH 는 본문만 번역되고 지역명은 영어로 나오는 반쪽
 * 상태로 운영됐고, 육안 검증이 가능한 사람도 없어 오역이 나도 발견되지 않았다
 * (기계적 검사 — 한글 잔존·기호 개수 — 는 오역이어도 전부 통과한다).
 *
 * <p><b>새 언어를 추가할 때는 이 enum 만으로 끝나지 않는다.</b> 함께 손봐야 할 곳:
 * <ol>
 *   <li>{@link OpenAiTranslationClient} 의 {@code LANGUAGE_NAMES} — 빠뜨리면 런타임에
 *       {@code IllegalArgumentException}</li>
 *   <li>법정동 명칭 시드 마이그레이션 — 없으면 지역명만 영어로 폴백해 본문과 어긋난다
 *       (VI/TH 를 제거한 사유가 바로 이것이다). 기본 시드와 함께, 통합 코드 보충
 *       마이그레이션({@code V113})이 다루는 {@code 12xx} 범위도 빠뜨리지 말 것</li>
 *   <li>프론트엔드 세 곳 — {@code src/constants/language.ts}(선택지),
 *       {@code src/constants/i18n.ts}(UI 문자열 리소스),
 *       {@code src/lib/i18n.ts}(i18next 리소스 등록).
 *       <b>앞 둘만 고치고 등록을 빠뜨려도 타입 검사는 통과한다</b> — {@code LangCode} 와
 *       i18next {@code resources} 의 키를 엮어주는 장치가 없기 때문이다
 *       ({@code declare module "i18next"} / {@code CustomTypeOptions} 미사용).
 *       빌드가 깨지는 대신 런타임에 {@code fallbackLng: "ko"} 로 조용히 폴백하므로
 *       오히려 발견이 늦다. 세 곳을 함께 확인할 것</li>
 * </ol>
 * 시드를 만들 때는 <b>표기 구조</b>도 기존 언어와 맞추는 편이 좋다 — 한국어와 같이 공백으로
 * 구분하고 큰 단위에서 작은 단위 순({@code 忠清南道 牙山市}, {@code Chungcheongnam-do Asan-si})
 * 이다. 현재 시드가 모두 이 구조를 따른다.
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

    /** DB 저장 / 번역 대상 언어 코드로 쓰이는 대문자 표기 */
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
