package com.disaster.alert.alertapi.domain.disasteralert.dto;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertSearchRequest {
    private String region;
    private String districtCode;
    private LocalDate startDate; //ISO 8601 형식 (yyyy-MM-dd)
    private LocalDate endDate;   //ISO 8601 형식 (yyyy-MM-dd)
    private String type;
    private DisasterLevel level;
    private String keyword;
}