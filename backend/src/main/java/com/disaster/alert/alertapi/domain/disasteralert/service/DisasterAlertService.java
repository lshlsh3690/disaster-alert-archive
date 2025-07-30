package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.api.DisasterOpenApiClient;
import com.disaster.alert.alertapi.domain.disasteralert.dto.*;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import com.disaster.alert.alertapi.domain.legaldistrict.repository.LegalDistrictRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
                .OriginalRegion(dto.getRegion())
                .build();

        for (String regionName : regionNames) {
            List<LegalDistrict> legalDistricts = legalDistrictRepository.findAllByName(regionName);

            if (legalDistricts.isEmpty()) {
                log.warn("법정동 데이터가 없습니다: {}", regionName);
                continue;
            }

            LegalDistrict legalDistrict;
            if (legalDistricts.size() > 1) {
                legalDistrict = legalDistricts.stream()
                        .filter(ld -> ld.isActive() || "존재".equals(ld.getIsActiveString()))
                        .findFirst()
                        .orElse(legalDistricts.get(0));
                log.warn("법정동 이름이 중복됩니다: {}", regionName);
            } else {
                legalDistrict = legalDistricts.get(0);
            }

            alert.addRegion(legalDistrict);
        }

        return alert;
    }

    private List<DisasterAlert> toEntities(DisasterAlertDto dto) {
        // 지역 이름을 쉼표로 분리하고 공백 제거
        // example: "서울시 , 경기 전체" → ["서울시", "경기"]
        List<String> regionNames = Arrays.stream(dto.getRegion().split(","))
                .flatMap(region -> {
                    // 법정동 이름을 정리하여 중복된 단어 제거
                    String cleanedRegion = cleanRegionString(region);
                    return sanitizeRegionNames(cleanedRegion).stream();
                })
                .toList();

        //법정동에 존재하지 않는 지역명
        if (dto.getRegion().equals("임진강 수계지역(경기도 연천군,파주시),경기도 임진강")) {
            // 임진강 수계지역은 특별한 예외 처리
            regionNames = List.of("경기도 연천군", "경기도 파주시");
        }

        return regionNames.stream()
                .map(regionName -> {
                    List<LegalDistrict> legalDistricts = legalDistrictRepository.findAllByName(regionName);


                    if (legalDistricts.isEmpty()) {
                        log.warn("법정동 데이터가 없습니다: {}", regionName);
                        return null; // 법정동이 없으면 null 반환
                    }

                    LegalDistrict legalDistrict = null;
                    if (legalDistricts.size() > 1) {
                        // 중복된 법정동이 있다면 우선적으로 활성화된 법정동을 찾고, 없으면 첫 번째 것을 사용
                        legalDistrict = legalDistricts.stream()
                                .filter(ld -> ld.isActive() || ld.getIsActiveString().equals("존재"))
                                .findFirst()
                                .orElse(legalDistricts.get(0)); // 활성화된 법정동이 없으면 첫 번째 것 사용
                        log.warn("법정동 이름이 중복됩니다: {}", regionName);
                    } else {
                        // 법정동이 하나만 있다면 그 법정동 사용
                        legalDistrict = legalDistricts.get(0);
                    }

                    DisasterAlert build = DisasterAlert.builder()
                            .sn(dto.getSn())
                            .message(dto.getMessage())
                            .createdAt(parseDateTime(dto.getCreatedAt()))
                            .emergencyLevel(dto.getEmergencyLevel() == null ? null : DisasterLevel.fromDescription(dto.getEmergencyLevel()))
                            .disasterType(dto.getDisasterType())
                            .modifiedDate(dto.getModifiedDate() == null ? null : parseDateTime(dto.getModifiedDate()))
                            .OriginalRegion(dto.getRegion()) // 원본 지역명 저장
                            .build();
                    build.addRegion(legalDistrict); // 재난문자와 법정동 연결

                    return build;
                })
                .toList();
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
            // 가장 긴 매칭만 가져오기
            List<String> tokens = Arrays.asList(region.split(" "));
            for (int i = tokens.size(); i > 0; i--) {
                String candidate = String.join(" ", tokens.subList(0, i));
                if (legalDistrictRepository.existsByName(candidate)) {
                    result.add(candidate); // 가장 긴 매칭 하나만 추가
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
    @Transactional
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

    public Page<DisasterAlertResponseDto> searchAlerts(String region, String districtCode, LocalDate startDate, LocalDate endDate, String type, DisasterLevel level, String keyword, Pageable pageable) {
        AlertSearchCondition alertSearchCondition = AlertSearchCondition.builder()
                .region(region)
                .districtCode(districtCode)
                .startDate(startDate)
                .endDate(endDate)
                .type(type)
                .level(level)
                .keyword(keyword)
                .build();

        Page<DisasterAlert> result = disasterAlertRepository.searchAlerts(alertSearchCondition, pageable);

        return result.map(DisasterAlertResponseDto::from);
    }

    public DisasterAlertStatResponse getStats(String region, String districtCode, LocalDate startDate, LocalDate endDate, String type, DisasterLevel level, String keyword) {
        AlertSearchCondition alertSearchCondition = AlertSearchCondition.builder()
                .region(region)
                .districtCode(districtCode)
                .startDate(startDate)
                .endDate(endDate)
                .type(type)
                .level(level)
                .keyword(keyword)
                .build();


        List<DisasterAlert> alerts = disasterAlertRepository.disasterAlertsBySearchCondition(alertSearchCondition);
        long totalCount = alerts.size();

        // 지역별 카운트
        Map<String, Long> regionCountMap = alerts.stream()
                .flatMap(alert -> alert.getDisasterAlertRegions().stream())
                .collect(Collectors.groupingBy(
                        r -> r.getLegalDistrict().getName(),
                        Collectors.counting()
                ));

        // 레벨별 카운트
        Map<DisasterLevel, Long> levelCountMap = alerts.stream()
                .filter(a -> a.getEmergencyLevel() != null)
                .collect(Collectors.groupingBy(
                        DisasterAlert::getEmergencyLevel,
                        Collectors.counting()
                ));

        // 타입별 카운트
        Map<String, Long> typeCountMap = alerts.stream()
                .filter(a -> a.getDisasterType() != null)
                .collect(Collectors.groupingBy(
                        DisasterAlert::getDisasterType,
                        Collectors.counting()
                ));

        // DTO로 변환
        List<DisasterAlertStatResponse.RegionStat> regionStats = regionCountMap.entrySet().stream()
                .map(e -> new DisasterAlertStatResponse.RegionStat(e.getKey(), e.getValue()))
                .toList();

        List<DisasterAlertStatResponse.LevelStat> levelStats = levelCountMap.entrySet().stream()
                .map(e -> new DisasterAlertStatResponse.LevelStat(e.getKey(), e.getValue()))
                .toList();

        List<DisasterAlertStatResponse.TypeStat> typeStats = typeCountMap.entrySet().stream()
                .map(e -> new DisasterAlertStatResponse.TypeStat(e.getKey(), e.getValue()))
                .toList();

        return new DisasterAlertStatResponse(totalCount, regionStats, levelStats, typeStats);
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
}