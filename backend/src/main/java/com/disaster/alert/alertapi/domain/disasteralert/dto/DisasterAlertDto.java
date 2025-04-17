package com.disaster.alert.alertapi.domain.disasteralert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DisasterAlertDto {

    @JsonProperty("MSG_CN")
    private String message;

    @JsonProperty("RCPTN_RGN_NM")
    private String region;

    @JsonProperty("CRT_DT")
    private String createdAt;

    @JsonProperty("EMRG_STEP_NM")
    private String emergencyStep;

    @JsonProperty("DST_SE_NM")
    private String disasterType;

    @JsonProperty("MDFCN_YMD")
    private String modifiedDate;

    @JsonProperty("SN")
    private Long sn;
}
