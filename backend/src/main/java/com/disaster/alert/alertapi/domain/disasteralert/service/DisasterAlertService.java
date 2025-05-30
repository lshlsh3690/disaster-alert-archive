package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertDto;
import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterApiResponse;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.region.model.LegalDistrict;
import com.disaster.alert.alertapi.domain.region.repoistory.LegalDistrictRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisasterAlertService {
    private final DisasterAlertRepository disasterAlertRepository;
    private final LegalDistrictRepository legalDistrictRepository;
    private final ObjectMapper objectMapper;

    public void saveData(String raw) {
        try {
            DisasterApiResponse response = objectMapper.readValue(raw, DisasterApiResponse.class);

            if (!"00".equals(response.getHeader().getResultCode())) {
                log.warn("재난문자 API 응답 오류: {}", response.getHeader().getResultMsg());
                return;
            }

            List<DisasterAlertDto> dtos = response.getBody();
            if (dtos == null || dtos.isEmpty()) {
                log.info("재난문자 데이터가 없습니다.");
                return;
            }

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
        String regionName = dto.getRegion();

        LegalDistrict byName = legalDistrictRepository.findByName(regionName)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 지역: " + regionName));

        return DisasterAlert.builder()
                .sn(dto.getSn())
                .message(dto.getMessage())
                .createdAt(parseDateTime(dto.getCreatedAt()))
                .emergencyLevel(DisasterLevel.fromDescription(dto.getEmergencyLevel()))
                .disasterType(dto.getDisasterType())
                .modifiedDate(parseDateTime(dto.getModifiedDate()))
                .legalDistrict(byName)
                .build();
    }

    private LocalDateTime parseDateTime(String str) {
        // 예: "2025/05/27 09:19:50.000000000" → "2025/05/27 09:19:50"
        if (str.contains(".")) {
            str = str.substring(0, str.indexOf(".")); // 소수점 이하 제거
        }
        return LocalDateTime.parse(str, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
    }
}
