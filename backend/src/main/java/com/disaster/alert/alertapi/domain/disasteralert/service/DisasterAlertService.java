package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.api.DisasterOpenApiClient;
import com.disaster.alert.alertapi.domain.disasteralert.dto.*;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertTranslationRepository;
import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import com.disaster.alert.alertapi.domain.legaldistrict.service.LegalDistrictTranslationService;
import com.disaster.alert.alertapi.global.service.LegalDistrictCache;
import com.disaster.alert.alertapi.global.translation.SupportedLanguage;
import com.disaster.alert.alertapi.global.translation.TranslationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisasterAlertService {
    private final DisasterAlertRepository disasterAlertRepository;
    private final DisasterAlertTranslationRepository translationRepository;

    private final DisasterOpenApiClient disasterOpenApiClient;
    private final ObjectMapper objectMapper;

    private final LegalDistrictCache legalDistrictCache;

    // ─── 다국어 응답용 의존성 ──────────────────────────────
    private final TranslationService translationService;                          // lazy 번역 (message/disasterType)
    private final LegalDistrictTranslationService legalDistrictTranslationService; // 법정동 번역 조회

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public List<Long> saveData(String raw) {
        if (raw == null || raw.isBlank()) {
            log.warn("saveData: 원시 응답이 null/blank 입니다. 저장을 건너뜁니다.");
            return List.of();
        }
        try {
            DisasterApiResponse response = objectMapper.readValue(raw, DisasterApiResponse.class);

            if (checkAPIFailure(response)) return List.of();

            List<DisasterAlertDto> dtos = response.getBody();
            if (dtos == null || dtos.isEmpty()) {
                log.info("재난문자 데이터가 없습니다.");
                return List.of();
            }

            // 지역 이름을 정리하여 공백 제거 및 "전체" 제거
            dtos.forEach(dto -> dto.setRegion(cleanRegionString(dto.getRegion())));

            List<Long> incomingSnList = dtos.stream()
                    .map(DisasterAlertDto::getSn)
                    .toList();

            List<Long> existingSnList = disasterAlertRepository.findExistingSn(incomingSnList);
            Set<Long> existingSnSet = new HashSet<>(existingSnList);

            List<DisasterAlert> newAlerts = dtos.stream()
                    .filter(dto -> !existingSnSet.contains(dto.getSn()))
                    .map(this::toEntity)
                    .toList();

            if (newAlerts.isEmpty()) {
                log.info("새로운 재난문자가 없습니다.");
                return List.of();
            }

            // 변경 - 충돌 시 무시
            List<Long> savedIds = new ArrayList<>();
            try {
                disasterAlertRepository.saveAll(newAlerts);
                savedIds = newAlerts.stream().map(DisasterAlert::getId).toList();
            } catch (Exception e) {
                // 중복 키 충돌 시 한 건씩 저장 시도
                for (DisasterAlert alert : newAlerts) {
                    try {
                        disasterAlertRepository.save(alert);
                        savedIds.add(alert.getId());
                    } catch (Exception ex) {
                        log.warn("중복 SN 건너뜀: {}", alert.getSn());
                    }
                }
            }
            log.info("재난문자 {}건 저장 완료", savedIds.size());
            return savedIds;
        } catch (Exception e) {
            log.error("재난문자 저장 중 오류 발생", e);
            return List.of();
        }
    }

    private LocalDateTime parseDateTime(String str) {
        if (str.contains("-")) {
            str = str.replace("-", "/"); // "-"를 "/"로 변경
            str += " 00:00:00"; // 시간 정보가 없으면 기본값 추가
        }
        // 예: "2025/05/27 09:19:50.000000000" → "2025/05/27 09:19:50"
        if (str.contains(".")) {
            str = str.substring(0, str.indexOf(".")); // 소수점 이하 제거
        }
        return LocalDateTime.parse(str, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
    }

    private static boolean checkAPIFailure(DisasterApiResponse response) {
        if (!"00".equals(response.getHeader().getResultCode())) {
            log.warn("재난문자 API 응답 오류: {}", response.getHeader().getResultMsg());
            return true;
        } else if (response.getBody() == null || response.getBody().isEmpty()) {
            log.warn("재난문자 데이터가 없습니다.");
            return true;

        }
        return false;
    }

    private DisasterAlert toEntity(DisasterAlertDto dto) {
        List<String> regionNames = new ArrayList<>(
                Arrays.stream(dto.getRegion().split(","))
                        .flatMap(region -> {
                            String cleanedRegion = cleanRegionString(region);
                            return sanitizeRegionNames(cleanedRegion).stream();
                        })
                        .toList()
        );

        if (dto.getRegion().contains("임진강 수계지역(경기도 연천군,파주시),경기도 임진강")) {
            regionNames = new ArrayList<>(regionNames);
            regionNames.add("경기도 연천군");
            regionNames.add("경기도 파주시");
        }

        DisasterAlert alert = DisasterAlert.builder()
                .sn(dto.getSn())
                .message(dto.getMessage())
                .createdAt(parseDateTime(dto.getCreatedAt()))
                .emergencyLevel(dto.getEmergencyLevel() == null ? null : DisasterLevel.fromDescription(dto.getEmergencyLevel()))
                .disasterType(dto.getDisasterType())
                .modifiedDate(dto.getModifiedDate() == null ? null : parseDateTime(dto.getModifiedDate()))
                .originalRegion(dto.getRegion())
                .build();

        Set<String> attachedCodes = new HashSet<>();
        for (String regionName : regionNames) {
            List<LegalDistrict> legalDistricts = legalDistrictCache.get(regionName);// 캐시에서 법정동 조회

            if (legalDistricts.isEmpty()) {
                log.warn("법정동 데이터가 없습니다: {}", regionName);
                continue;
            }

            // 법정동이 여러 개일 경우 활성화된 법정동을 우선 선택
            LegalDistrict picked = (legalDistricts.size() > 1)
                    ? legalDistricts.stream()
                    .filter(ld -> ld.isActive() || "존재".equals(ld.getIsActiveString()))
                    .findFirst()
                    .orElse(legalDistricts.get(0))
                    : legalDistricts.get(0);

            String code = picked.getCode();
            if (!attachedCodes.add(code)) continue;
            alert.addRegionCode(code);
        }

        return alert;
    }

    private String cleanRegionString(String region) {
        return Arrays.stream(region.split(","))
                .map(String::trim)
                .map(r -> r.endsWith("전체") ? r.replace("전체", "").trim() : r)
                .collect(Collectors.joining(","));
    }

    private List<String> sanitizeRegionNames(String regionRaw) {
        List<String> regions = Arrays.stream(regionRaw.split(","))
                .map(String::trim)
                .map(this::removeDuplicatePrefix) // 중복된 단어 제거
                .toList();

        List<String> result = new ArrayList<>();

        for (String region : regions) {
            // 지역이름 : "세종특별자치시 가람동 1동"
            List<String> tokens = Arrays.asList(region.split(" "));
            // 가장 긴 법정동 이름을 찾기 위해 뒤에서부터 검사
            for (int i = tokens.size(); i > 0; i--) {
                // 첫번째 candidate : "세종특별자치시 가람동 1동"
                // 두번째 candidate : "세종특별자치시 가람동"
                // 세번째 candidate : "세종특별자치시"
                String candidate = String.join(" ", tokens.subList(0, i));
                if (!legalDistrictCache.get(candidate).isEmpty()) {
                    result.add(candidate); // 법정동이 존재하면 추가
                    break;
                }
            }
        }

        return result;
    }

    private String removeDuplicatePrefix(String input) {
        // 예: "세종특별자치시 세종특별자치시 가람동" → "세종특별자치시 가람동"
        String[] parts = input.split(" ");
        Set<String> seen = new LinkedHashSet<>();

        for (String part : parts) {
            seen.add(part); // LinkedHashSet: 중복 제거 + 순서 유지
        }

        return String.join(" ", seen);
    }

    /**
     * 재난문자 데이터를 초기화합니다.
     */
    public void initAllDisasterData() {
        try {
            // 1. 첫 번째 호출로 totalCount만 확인
            int numOfRows = 1000;
            int firstPage = 1;
            String raw = disasterOpenApiClient.fetchData(firstPage, numOfRows);
            if (raw == null || raw.isBlank()) {
                log.warn("initAllDisasterData: 첫 페이지 응답이 없습니다. 초기화를 중단합니다.");
                return;
            }
            DisasterApiResponse response = objectMapper.readValue(raw, DisasterApiResponse.class);

            if (checkAPIFailure(response)) return;

            int totalCount = response.getTotalCount();
            long existingCount = disasterAlertRepository.count();

            // 2. 이미 저장된 데이터 수와 같으면 중단
            if (totalCount == existingCount) {
                log.info("재난문자 데이터가 최신 상태입니다. 총 {}건", totalCount);
                return;
            }

            // 3. 저장 시작 (1000개 단위 페이지 순차 저장)
            int totalPages = (int) Math.ceil((double) totalCount / numOfRows);

            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int page = 1; page <= totalPages; page++) {
                final int currentPage = page;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        String pageRaw = disasterOpenApiClient.fetchData(currentPage, numOfRows);
                        if (pageRaw == null || pageRaw.isBlank()) {
                            log.warn("page {} 응답 없음, 건너뜀", currentPage);
                            return;
                        }
                        this.saveData(pageRaw);
                        log.info("page {} 저장 완료", currentPage);
                    } catch (Exception e) {
                        log.error("page {} 저장 오류: {}", currentPage, e.getMessage());
                    }
                }, executor);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executor.shutdown();
            log.info("총 {}건 재난문자 초기화 완료", totalCount);
        } catch (Exception e) {
            log.error("DisasterAlertService.initAllDisasterData() 오류 발생", e);
        }
    }

    // ============================================================================
    //  검색 / 조회 — 다국어 지원
    // ============================================================================

    /**
     * 재난문자 검색 (페이지네이션).
     *
     * @param lang 응답 언어 ("ko"/null = 한국어, "en"/"ja"/"zh" = 번역본)
     */
    public Page<DisasterAlertResponseDto> searchAlerts(AlertSearchRequest cond, Pageable pageable, String lang) {
        Page<DisasterAlert> result = disasterAlertRepository.searchAlerts(cond, pageable);

        // 1) 기본 한국어 DTO 변환
        List<DisasterAlert> alerts = result.getContent();
        List<DisasterAlertResponseDto> dtos = alerts.stream()
                .map(DisasterAlertResponseDto::from)
                .toList();

        // 2) 다국어 응답 후처리
        SupportedLanguage.fromRequestParam(lang).ifPresent(language ->
                applyTranslationsToList(alerts, dtos, language)
        );

        return new PageImpl<>(dtos, pageable, result.getTotalElements());
    }

    /**
     * 공식 + 사용자 제보 통합 검색 (페이지네이션).
     *
     * <p>USER 제보는 번역 시스템이 없으므로 OFFICIAL 항목만 번역 적용.
     */
    public Page<CombinedAlertResponse> searchCombined(AlertSearchRequest req, String source, Pageable pageable, String lang) {
        Page<CombinedAlertResponse> page = disasterAlertRepository.searchCombined(req, source, pageable);

        SupportedLanguage.fromRequestParam(lang).ifPresent(language ->
                applyTranslationsToCombined(page.getContent(), language)
        );

        return page;
    }

    public DisasterAlertStatResponse getStats(AlertSearchRequest request) {
        long total = disasterAlertRepository.countAlerts(request);
        List<DisasterAlertStatResponse.RegionStat> regionStats = disasterAlertRepository.countByRegion(request);
        List<DisasterAlertStatResponse.LevelStat> levelStats = disasterAlertRepository.countByLevel(request);
        List<DisasterAlertStatResponse.TypeStat> typeStats = disasterAlertRepository.countByType(request);
        return new DisasterAlertStatResponse(total, regionStats, levelStats, typeStats);
    }

    public DashboardSummaryResponse getDashboardSummary(long todayUserCount, long totalUserCount) {
        long todayOfficial = disasterAlertRepository.countToday();
        long totalOfficial = disasterAlertRepository.count();
        long combined = totalOfficial + totalUserCount;
        return new DashboardSummaryResponse(todayOfficial, todayUserCount, totalUserCount, combined);
    }

    /**
     * 재난문자 상세 정보를 조회합니다.
     *
     * <p>다국어 동작:
     * <ul>
     *   <li>한국어 요청 ("ko"/null): 번역 필드 모두 null</li>
     *   <li>지원 언어 요청 ("en"/"ja"/"zh"): 캐시 없으면 DeepL 호출 → 저장 후 응답</li>
     * </ul>
     */
    @Transactional
    public DisasterAlertDetailDto getAlertDetail(Long id, String lang) {
        DisasterAlert alert = disasterAlertRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("재난문자를 찾을 수 없습니다: id=" + id));

        List<String> regionNames = disasterAlertRepository.legalDistrictNamesByAlertId(id);
        DisasterAlertDetailDto dto = new DisasterAlertDetailDto(alert, regionNames);

        SupportedLanguage.fromRequestParam(lang).ifPresent(language -> {
            // 1) 메시지/유형 번역 (없으면 lazy DeepL 호출)
            translationService.ensureTranslated(id, language);
            translationRepository.findByIdAlertIdAndIdLanguageCode(id, language.getDbCode())
                    .ifPresent(t -> {
                        dto.setTranslatedMessage(t.getTranslatedMessage());
                        dto.setTranslatedDisasterType(t.getTranslatedDisasterType());
                    });

            // 2) 지역명 번역 (legal_district_translation 조회, 없으면 한국어 fallback)
            List<String> codes = disasterAlertRepository.legalDistrictCodesByAlertId(id);
            Map<String, String> codeToTranslated = legalDistrictTranslationService.getTranslatedNames(codes, language.getDbCode());

            // 코드 순서대로 번역명을 매핑 (regionNames 와 codes 는 같은 순서로 정렬됨)
            List<String> translatedRegionNames = new ArrayList<>();
            for (int i = 0; i < codes.size(); i++) {
                String code = codes.get(i);
                String original = i < regionNames.size() ? regionNames.get(i) : null;
                translatedRegionNames.add(codeToTranslated.getOrDefault(code, original));
            }
            dto.setTranslatedRegionNames(translatedRegionNames);

            dto.setLanguage(language.getCode());
        });

        return dto;
    }

    /**
     * 최신 재난문자 N건을 조회합니다.
     *
     * @param lang 응답 언어 ("ko"/null = 한국어, "en"/"ja"/"zh" = 번역본)
     */
    @Transactional
    public List<LatestAlertResponse> getLatestAlert(int limit, String lang) {
        List<LatestAlertResponse> alerts = disasterAlertRepository.latestAlerts(PageRequest.of(0, limit));

        SupportedLanguage.fromRequestParam(lang).ifPresent(language ->
                applyTranslationsToLatest(alerts, language)
        );

        return alerts;
    }

    @Transactional(readOnly = true)
    public List<DisasterAlertStatResponse.RegionStat> countBySido(AlertSearchRequest request) {
        List<DisasterAlertStatResponse.RegionStat> regionStats = disasterAlertRepository.getStatsSido(request);
        log.info("지역별 재난문자 통계: {}", regionStats);
        return regionStats;
    }

    // ============================================================================
    //  다국어 응답 — 내부 헬퍼
    // ============================================================================

    /**
     * 검색 결과 페이지에 번역을 일괄 적용 (entity 기반).
     *
     * <p>흐름:
     * <ol>
     *   <li>누락된 (alertId, lang) 조합을 DeepL 로 일괄 번역 (lazy)</li>
     *   <li>번역 결과를 Map 으로 조회</li>
     *   <li>법정동 코드들을 legal_district_translation 에서 일괄 조회</li>
     *   <li>각 DTO 에 번역 필드 채우기</li>
     * </ol>
     */
    private void applyTranslationsToList(List<DisasterAlert> alerts, List<DisasterAlertResponseDto> dtos, SupportedLanguage language) {
        if (alerts.isEmpty()) return;

        List<Long> alertIds = alerts.stream().map(DisasterAlert::getId).toList();

        // 1) 누락된 항목 lazy 번역
        translationService.ensureTranslatedBatch(alertIds, language);

        // 2) 번역 Map 조회
        Map<Long, com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslation> translationMap =
                translationRepository.findByIdAlertIdInAndIdLanguageCode(alertIds, language.getDbCode())
                        .stream()
                        .collect(Collectors.toMap(t -> t.getId().getAlertId(), t -> t));

        // 3) 법정동 코드 일괄 수집 + 번역 조회
        Set<String> allCodes = alerts.stream()
                .flatMap(a -> Optional.ofNullable(a.getDisasterAlertRegions()).orElse(List.of()).stream())
                .map(r -> r.getLegalDistrict().getCode())
                .collect(Collectors.toSet());
        Map<String, String> codeToTranslated = legalDistrictTranslationService.getTranslatedNames(
                new ArrayList<>(allCodes), language.getDbCode());

        // 4) DTO 에 적용
        for (int i = 0; i < alerts.size(); i++) {
            DisasterAlert alert = alerts.get(i);
            DisasterAlertResponseDto dto = dtos.get(i);

            // 메시지/유형
            var t = translationMap.get(alert.getId());
            if (t != null) {
                dto.setTranslatedMessage(t.getTranslatedMessage());
                dto.setTranslatedDisasterType(t.getTranslatedDisasterType());
            }

            // 지역명
            List<String> translatedRegionNames = Optional.ofNullable(alert.getDisasterAlertRegions()).orElse(List.of())
                    .stream()
                    .map(r -> {
                        String code = r.getLegalDistrict().getCode();
                        String original = r.getLegalDistrict().getName();
                        return codeToTranslated.getOrDefault(code, original);
                    })
                    .toList();
            dto.setTranslatedRegionNames(translatedRegionNames);

            dto.setLanguage(language.getCode());
        }
    }

    /**
     * 통합 검색 결과에 번역을 일괄 적용.
     * OFFICIAL 항목만 번역, USER 항목은 한국어 그대로.
     */
    private void applyTranslationsToCombined(List<CombinedAlertResponse> items, SupportedLanguage language) {
        // OFFICIAL ID 추출
        List<Long> officialIds = items.stream()
                .filter(i -> i.getSource() == CombinedAlertResponse.Source.OFFICIAL)
                .map(CombinedAlertResponse::getId)
                .toList();

        if (officialIds.isEmpty()) return;

        // 1) lazy 번역
        translationService.ensureTranslatedBatch(officialIds, language);

        // 2) 번역 Map
        Map<Long, com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslation> translationMap =
                translationRepository.findByIdAlertIdInAndIdLanguageCode(officialIds, language.getDbCode())
                        .stream()
                        .collect(Collectors.toMap(t -> t.getId().getAlertId(), t -> t));

        // 3) 지역명 번역 — 일괄 (alertId, code) 쌍 조회
        Map<Long, List<String>> alertIdToCodes = new HashMap<>();
        for (Object[] row : disasterAlertRepository.findAlertIdAndCodePairs(officialIds)) {
            Long alertId = (Long) row[0];
            String code = (String) row[1];
            alertIdToCodes.computeIfAbsent(alertId, k -> new ArrayList<>()).add(code);
        }

        Set<String> allCodes = alertIdToCodes.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        Map<String, String> codeToTranslated = legalDistrictTranslationService.getTranslatedNames(
                new ArrayList<>(allCodes), language.getDbCode());

        // 4) DTO 적용
        for (CombinedAlertResponse item : items) {
            if (item.getSource() != CombinedAlertResponse.Source.OFFICIAL) continue;

            var t = translationMap.get(item.getId());
            if (t != null) {
                item.setTranslatedMessage(t.getTranslatedMessage());
                item.setTranslatedDisasterType(t.getTranslatedDisasterType());
            }

            List<String> codes = alertIdToCodes.getOrDefault(item.getId(), List.of());
            List<String> originalNames = Optional.ofNullable(item.getRegionNames()).orElse(List.of());
            List<String> translatedRegionNames = new ArrayList<>();
            for (int i = 0; i < codes.size(); i++) {
                String code = codes.get(i);
                String original = i < originalNames.size() ? originalNames.get(i) : null;
                translatedRegionNames.add(codeToTranslated.getOrDefault(code, original));
            }
            item.setTranslatedRegionNames(translatedRegionNames);

            item.setLanguage(language.getCode());
        }
    }

    /**
     * 최신 재난문자 목록에 번역을 일괄 적용.
     *
     * <p>topRegion 은 첫 번째 법정동 코드를 사용해 번역 (legal_district_translation).
     * 번역이 없으면 원본 한국어 유지.
     */
    private void applyTranslationsToLatest(List<LatestAlertResponse> alerts, SupportedLanguage language) {
        if (alerts.isEmpty()) return;

        List<Long> alertIds = alerts.stream().map(LatestAlertResponse::getId).toList();

        // 1) lazy 번역
        translationService.ensureTranslatedBatch(alertIds, language);

        // 2) 번역 Map
        Map<Long, com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslation> translationMap =
                translationRepository.findByIdAlertIdInAndIdLanguageCode(alertIds, language.getDbCode())
                        .stream()
                        .collect(Collectors.toMap(t -> t.getId().getAlertId(), t -> t));

        // 3) 지역명 번역 — (alertId → 첫 코드) 매핑
        Map<Long, String> alertIdToFirstCode = new HashMap<>();
        for (Object[] row : disasterAlertRepository.findAlertIdAndCodePairs(alertIds)) {
            Long alertId = (Long) row[0];
            String code = (String) row[1];
            // 가장 먼저 매칭된 코드만 사용 (대표 지역)
            alertIdToFirstCode.putIfAbsent(alertId, code);
        }
        Set<String> allCodes = new HashSet<>(alertIdToFirstCode.values());
        Map<String, String> codeToTranslated = legalDistrictTranslationService.getTranslatedNames(
                new ArrayList<>(allCodes), language.getDbCode());

        // 4) DTO 적용
        for (LatestAlertResponse alert : alerts) {
            var t = translationMap.get(alert.getId());
            if (t != null) {
                alert.setTranslatedMessage(t.getTranslatedMessage());
                alert.setTranslatedDisasterType(t.getTranslatedDisasterType());
            }

            String firstCode = alertIdToFirstCode.get(alert.getId());
            if (firstCode != null) {
                alert.setTranslatedTopRegion(
                        codeToTranslated.getOrDefault(firstCode, alert.getTopRegion())
                );
            } else {
                alert.setTranslatedTopRegion(alert.getTopRegion());
            }

            alert.setLanguage(language.getCode());
        }
    }
}
