package com.disaster.alert.alertapi.domain.disasteralert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 항목명(국문)	항목명(영문)	    항목크기	항목구분	항목설명
 일련번호	    SN		        22	    Y	    일련번호
 생성일시	    CRT_DT		    50	    Y	    생성일시
 메시지내용	MSG_CN		    4000	Y	    메시지내용
 수신지역명	RCPTN_RGN_NM    4000	Y	    수신지역명
 긴급단계명	EMRG_STEP_NM	100	    Y	    긴급단계명
 재해구분명	DST_SE_NM		100	    Y	    재해구분명
 등록일자	    REG_YMD		    50	    Y	    등록일자
 수정일자	    MDFCN_YMD		50	    Y	    수정일자
 */
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
    private String emergencyLevel;

    @JsonProperty("DST_SE_NM")
    private String disasterType;

    @JsonProperty("MDFCN_YMD")
    private String modifiedDate;

    @JsonProperty("SN")
    private Long sn;
}
