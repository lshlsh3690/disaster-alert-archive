package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.api.DisasterOpenApiClient;
import com.disaster.alert.alertapi.domain.disasteralert.dto.*;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import com.disaster.alert.alertapi.domain.legaldistrict.repository.LegalDistrictRepository;
import com.disaster.alert.alertapi.global.service.LegalDistrictCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisasterAlertService {
    private final DisasterAlertRepository disasterAlertRepository;
    private final LegalDistrictRepository legalDistrictRepository;

    private final DisasterOpenApiClient disasterOpenApiClient;
    private final ObjectMapper objectMapper;

    private final LegalDistrictCache legalDistrictCache;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void saveData(String raw) {
        try {
            DisasterApiResponse response = objectMapper.readValue(raw, DisasterApiResponse.class);

            if (checkAPIFailure(response)) return;

            List<DisasterAlertDto> dtos = response.getBody();
            if (dtos == null || dtos.isEmpty()) {
                log.info("재난문자 데이터가 없습니다.");
                return;
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
                return;
            }

            disasterAlertRepository.saveAll(newAlerts);
            log.info("재난문자 {}건 저장 완료", newAlerts.size());
        } catch (Exception e) {
            log.error("재난문자 저장 중 오류 발생", e);
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

            for (int page = 1; page <= totalPages; page++) {
                raw = disasterOpenApiClient.fetchData(page, numOfRows);
                this.saveData(raw);
                log.info("page {} 저장 완료", page);
            }

            log.info("총 {}건 재난문자 초기화 완료", totalCount);
        } catch (Exception e) {
            log.error("DisasterAlertService.initAllDisasterData() 오류 발생", e);
        }
    }

    public Page<DisasterAlertResponseDto> searchAlerts(AlertSearchRequest alertSearchCondition, Pageable pageable) {
        Page<DisasterAlert> result = disasterAlertRepository.searchAlerts(alertSearchCondition, pageable);

        return result.map(DisasterAlertResponseDto::from);
    }

    public DisasterAlertStatResponse getStats(AlertSearchRequest request) {
        long total = disasterAlertRepository.countAlerts(request);
        List<DisasterAlertStatResponse.RegionStat> regionStats = disasterAlertRepository.countByRegion(request);
        List<DisasterAlertStatResponse.LevelStat> levelStats = disasterAlertRepository.countByLevel(request);
        List<DisasterAlertStatResponse.TypeStat> typeStats = disasterAlertRepository.countByType(request);
        return new DisasterAlertStatResponse(total, regionStats, levelStats, typeStats);
    }

    /**
     * 재난문자 상세 정보를 조회합니다.
     *
     * @param id 재난문자 ID
     * @return 재난문자 상세 정보 DTO
     */
    public DisasterAlertDetailDto getAlertDetail(Long id) {
        DisasterAlert alert = disasterAlertRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("재난문자를 찾을 수 없습니다: id=" + id));

        List<String> regionNames = disasterAlertRepository.legalDistrictNamesByAlertId(id);

        return new DisasterAlertDetailDto(alert, regionNames);
    }

    /**
     * 최신 재난문자를 조회합니다.
     *
     * @return 최신 재난문자 DTO
     */
    public List<LatestAlertResponse> getLatestAlert(int limit) {
        return disasterAlertRepository.latestAlerts(PageRequest.of(0, limit));
    }
}