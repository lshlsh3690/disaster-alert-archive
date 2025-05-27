package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertDto;
import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterApiResponse;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisasterAlertService {
    private final DisasterAlertRepository disasterAlertRepository;
    private final ObjectMapper objectMapper;

    public void saveData(String raw) {
        try {
            DisasterApiResponse response = objectMapper.readValue(raw, DisasterApiResponse.class);

            if (!"00".equals(response.getHeader().getResultCode())) {
                log.warn("재난문자 API 응답 오류: {}", response.getHeader().getResultMsg());
                return;
            }

            List<DisasterAlertDto> dtos = response.getBody();

            List<Long> incomingSnList = dtos.stream()
                    .map(DisasterAlertDto::getSn)
                    .toList();

            List<Long> existingSnList = disasterAlertRepository.findExistingSn(incomingSnList);

            Set<Long> existingSnSet = new HashSet<>(existingSnList);

            List<DisasterAlert> newAlerts = dtos.stream()
                    .filter(dto -> !existingSnSet.contains(dto.getSn()))
                    .map(this::toEntity)
                    .toList();

            // 2. 지역명 추출 → 한번에 조회
//            Set<String> allRegionNames = newAlerts.stream()
//                    .flatMap(dto -> parseRegionNames(dto.getRegion()).stream())
//                    .collect(Collectors.toSet());

            disasterAlertRepository.saveAll(newAlerts);
            log.info("재난문자 {}건 저장 완료", newAlerts.size());
        } catch (Exception e) {
            log.error("재난문자 저장 중 오류 발생", e);
        }
    }

    private DisasterAlert toEntity(DisasterAlertDto dto) {
        return DisasterAlert.builder()
                .sn(dto.getSn())
                .message(dto.getMessage())
                .createdAt(parseDateTime(dto.getCreatedAt()))
                .emergencyLevel(dto.getEmergencyLevel())
                .disasterType(dto.getDisasterType())
                .modifiedDate(parseDate(dto.getModifiedDate()))
                .build();
    }

    private LocalDateTime parseDateTime(String str) {
        return LocalDateTime.parse(str, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
    }

    private LocalDate parseDate(String str) {
        return LocalDate.parse(str, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}
