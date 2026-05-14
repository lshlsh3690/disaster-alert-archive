package com.disaster.alert.alertapi.domain.legaldistrict.service;

import com.disaster.alert.alertapi.domain.legaldistrict.dto.SigunguResponse;
import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import com.disaster.alert.alertapi.domain.legaldistrict.repository.LegalDistrictRepository;
import com.disaster.alert.alertapi.global.translation.SupportedLanguage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LegalDistrictService {
    private final LegalDistrictRepository legalDistrictRepository;
    private final LegalDistrictTranslationService legalDistrictTranslationService;

    /**
     * 법정동 데이터를 초기화하는 메서드입니다.
     * 이 메서드는 애플리케이션 시작 시 호출되어 법정동 데이터를 초기화합니다.
     */
    public void saveAllLegalDistricts() {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("data/legal_district_init_file.csv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String line;

            // 첫 번째 줄은 헤더이므로 건너뜁니다.
            reader.readLine();

            List<LegalDistrict> districts = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1); // 빈 값도 포함되도록

                if (parts.length < 3) continue; // 잘못된 라인 건너뛰기

                String code = parts[0].trim();
                String name = parts[1].trim();
                String isActiveStr = parts[2].trim();

                if (legalDistrictRepository.existsByCode(code)) {
                    continue;
                }

                boolean isActive = "존재".equals(isActiveStr);

                districts.add(LegalDistrict.builder()
                        .code(code)
                        .name(name)
                        .isActive(isActive)
                        .isActiveString(isActiveStr)
                        .build());
            }
            if (!districts.isEmpty()) {
                legalDistrictRepository.saveAll(districts);
                log.info("법정동 {}건 저장 완료", districts.size());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 시/도에 속한 시/군/구 목록을 다국어로 조회한다.
     *
     * <p><b>응답 형태</b>: {@code [{name: "강남구", translatedName: "Gangnam-gu"}, ...]}
     * <ul>
     *   <li>{@code name} — 한글 원본. 프론트가 폼 제출 시 백엔드 검색 키로 사용.</li>
     *   <li>{@code translatedName} — 시도 prefix 가 제거된 시군구 번역명.
     *       한국어/미지원 lang 요청 시 null.</li>
     * </ul>
     *
     * <p><b>왜 시도 prefix 를 제거하나?</b>
     * legal_district_translation 에는 풀네임으로 저장되어 있다 (예: "Seoul Gangnam-gu").
     * 하지만 시/도 드롭다운에서 이미 시도가 선택된 상태라 "Gangnam-gu" 만 보여주는 게 자연스럽다.
     *
     * <p><b>처리 흐름</b>
     * <ol>
     *   <li>한글 시군구 이름 리스트 조회 (기존 native 쿼리 재사용)</li>
     *   <li>한국어/미지원 lang 이면 translatedName 모두 null 로 반환 (분기 종료)</li>
     *   <li>시군구 풀네임("시도 + 시군구") 으로 LegalDistrict 일괄 조회 → code 확보</li>
     *   <li>시도 자체의 code 도 확보 (영어 prefix 제거용)</li>
     *   <li>{@code LegalDistrictTranslationService} 로 영어 fallback 포함 번역 조회</li>
     *   <li>시군구 번역에서 시도 prefix 제거 후 매핑</li>
     * </ol>
     *
     * @param sido 시/도 한글 이름 (예: "서울특별시")
     * @param lang 요청 언어 ("ko"/"en"/"ja"/"zh"). 미지원 값은 안전하게 ko 처럼 처리.
     * @return 시군구 응답 리스트
     */
    @Transactional(readOnly = true)
    public List<SigunguResponse> getSigunguList(String sido, String lang) {
        // 1) 한글 시군구 리스트 (기존 native 쿼리 그대로)
        List<String> sigunguNames = legalDistrictRepository.findSigunguBySido(sido);

        // 2) 한국어 또는 미지원 lang → 번역 시도 안 함 (DB 추가 쿼리 없음)
        Optional<SupportedLanguage> langOpt = SupportedLanguage.fromRequestParam(lang);
        if (langOpt.isEmpty()) {
            return sigunguNames.stream()
                    .map(name -> new SigunguResponse(name, null))
                    .toList();
        }
        SupportedLanguage language = langOpt.get();

        // 3) 시군구 풀네임("서울특별시 강남구") → LegalDistrict.code 매핑
        List<String> fullNames = sigunguNames.stream()
                .map(s -> sido + " " + s)
                .toList();
        Map<String, String> nameToCode = legalDistrictRepository
                .findByNameInOrderByCodeAsc(fullNames)
                .stream()
                .collect(Collectors.toMap(
                        LegalDistrict::getName,
                        LegalDistrict::getCode,
                        // 동일 이름이 여러 법정동에 있을 경우(드물지만) 첫 번째 유지
                        (existing, replacement) -> existing
                ));

        // 4) 시도 자체의 code 확보 (예: "서울특별시" → "1100000000")
        //    이후 시도 영어 prefix 를 알아야 시군구만 추출 가능 ("Seoul Gangnam-gu" → "Gangnam-gu")
        String sidoCode = legalDistrictRepository.findByName(sido)
                .map(LegalDistrict::getCode)
                .orElse(null);

        // 5) 번역 대상 code 모음: 시군구 + 시도
        List<String> codesToTranslate = new ArrayList<>(nameToCode.values());
        if (sidoCode != null) {
            codesToTranslate.add(sidoCode);
        }

        // 6) 영어 fallback 포함 일괄 번역 조회 (어제 PR3 의 새 메서드 활용)
        //    JA/ZH 요청이면 누락분에 대해 영어로 자동 fallback
        Map<String, String> codeToTranslated = legalDistrictTranslationService
                .getTranslatedNamesWithEnglishFallback(codesToTranslate, language.getDbCode());

        String sidoTranslated = sidoCode != null ? codeToTranslated.get(sidoCode) : null;

        // 7) 최종 매핑: 시군구 번역에서 시도 prefix 제거
        return sigunguNames.stream()
                .map(name -> {
                    String fullName = sido + " " + name;
                    String code = nameToCode.get(fullName);
                    String fullTranslated = code != null ? codeToTranslated.get(code) : null;
                    String sigunguOnly = stripSidoPrefix(fullTranslated, sidoTranslated);
                    return new SigunguResponse(name, sigunguOnly);
                })
                .toList();
    }

    /**
     * 시군구 풀네임 번역에서 시도 영어 prefix 를 제거한다.
     *
     * <p>예: stripSidoPrefix("Seoul Gangnam-gu", "Seoul") → "Gangnam-gu"
     *
     * <p>안전망: 시도 prefix 가 매칭되지 않으면 풀네임을 그대로 반환 (헝클어지지 않도록).
     */
    private String stripSidoPrefix(String fullTranslated, String sidoTranslated) {
        if (fullTranslated == null) {
            return null;
        }
        if (sidoTranslated != null && fullTranslated.startsWith(sidoTranslated + " ")) {
            return fullTranslated.substring(sidoTranslated.length() + 1);
        }
        return fullTranslated;
    }
}
