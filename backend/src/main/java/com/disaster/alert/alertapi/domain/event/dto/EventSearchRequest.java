package com.disaster.alert.alertapi.domain.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 이벤트 목록 검색 조건. 재난문자 검색({@code AlertSearchRequest})과 유사하나,
 * 이벤트는 대표 정보(primary_*) 1개만 갖는 사건 단위라 필터 의미가 다르다.
 *
 * <ul>
 *   <li>{@code type}      — 유형 exact ({@code primary_disaster_type})</li>
 *   <li>{@code region}    — 시도 또는 "시도 시군구" 합성. {@code primary_region_name} prefix(LIKE 'region%').
 *                            broadcast/전국 이벤트는 대표지역 1개라 <b>근사치</b>(전국 이벤트는 region_code=null 이라 빠질 수 있음).</li>
 *   <li>{@code districtCode} — 선택. {@code primary_region_code} exact</li>
 *   <li>{@code startDate}/{@code endDate} — 기간 <b>겹침(overlap)</b>: first_alert_at ≤ end AND last_alert_at ≥ start</li>
 *   <li>{@code keyword}   — {@code event_title} contains (본문 검색 아님)</li>
 *   <li>{@code active}    — null=전체, true=진행 중, false=지난 사건 (조회 시점 파생)</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventSearchRequest {
    private String type;
    private String region;
    private String districtCode;
    private LocalDate startDate; // ISO 8601 (yyyy-MM-dd)
    private LocalDate endDate;   // ISO 8601 (yyyy-MM-dd)
    private String keyword;
    private Boolean active;
}
