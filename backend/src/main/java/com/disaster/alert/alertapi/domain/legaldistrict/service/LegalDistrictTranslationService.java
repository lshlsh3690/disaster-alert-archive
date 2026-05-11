package com.disaster.alert.alertapi.domain.legaldistrict.service;

import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrictTranslation;
import com.disaster.alert.alertapi.domain.legaldistrict.repository.LegalDistrictTranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 법정동 번역 조회 헬퍼.
 *
 * <p><b>역할</b>: 재난문자 응답을 만들 때 "여러 법정동 코드 → 번역명" 매핑이 자주 필요한데,
 * 매번 직접 레포지토리를 호출하면 결과를 Map 으로 변환하는 로직이 중복된다.
 * 이 서비스가 그 변환을 캡슐화한다.
 *
 * <p><b>사용 예시 (PR 2 에서 적용 예정)</b>
 * <pre>{@code
 * List<String> codes = List.of("1100000000", "1111000000");
 * Map<String, String> translated = service.getTranslatedNames(codes, "EN");
 * // → { "1100000000": "Seoul", "1111000000": "Seoul Jongno-gu" }
 *
 * // 번역이 없는 코드는 Map 에 없음 → 호출 측에서 한국어 fallback 처리
 * String name = translated.getOrDefault(code, koreanFallback);
 * }</pre>
 */
@Service
@RequiredArgsConstructor
public class LegalDistrictTranslationService {

    private final LegalDistrictTranslationRepository repository;

    /**
     * 여러 법정동 코드를 특정 언어로 일괄 번역해서 Map 으로 반환.
     *
     * <p><b>주의</b>: 번역이 없는 코드는 결과 Map 에 포함되지 않는다.
     * 호출 측에서 {@code Map.getOrDefault()} 또는 원본 한국어로 fallback 해야 한다.
     * 이렇게 설계한 이유: 중국어/일본어 시드 데이터는 점진적으로 추가될 예정이라
     * "모든 법정동이 모든 언어로 번역되어 있다"는 가정을 두지 않는다.
     *
     * @param codes 조회할 법정동 코드 리스트 (null/empty 허용)
     * @param languageCode 언어 코드 (예: "EN", "JA", "ZH")
     * @return code → 번역명 매핑. 번역이 없는 코드는 결과에서 제외.
     */
    @Transactional(readOnly = true)
    public Map<String, String> getTranslatedNames(List<String> codes, String languageCode) {
        // null / empty 가드 — 빈 IN 절은 DB 마다 동작이 다르므로 미리 차단
        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }
        return repository.findByIdCodeInAndIdLanguageCode(codes, languageCode)
                .stream()
                .collect(Collectors.toMap(
                        ldt -> ldt.getId().getCode(),  // key: 법정동 코드
                        LegalDistrictTranslation::getName  // value: 번역명
                ));
    }
}
