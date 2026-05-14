package com.disaster.alert.alertapi.domain.legaldistrict.controller;

import com.disaster.alert.alertapi.domain.legaldistrict.dto.SigunguResponse;
import com.disaster.alert.alertapi.domain.legaldistrict.service.LegalDistrictService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/districts")
@RequiredArgsConstructor
public class LegalDistrictController {

    private final LegalDistrictService legalDistrictService;

    /**
     * 시/도에 속한 시/군/구 목록 조회 (다국어 응답 지원).
     *
     * <p><b>응답 구조 변경 안내 (브레이킹)</b>: 이전에는 {@code List<String>} 을 반환했으나,
     * 다국어 응답을 위해 {@code List<SigunguResponse>} 로 변경됨.
     * 호출 측(프론트 {@code fetchSigungu})도 함께 수정 필요.
     *
     * @param sido 시/도 한글 이름 (예: "서울특별시"). 폼 제출 시에도 한글로 들어옴.
     * @param lang 응답 언어 — "ko"(기본)/"en"/"ja"/"zh".
     *             "ko"/null/미지원 값은 {@code translatedName} 모두 null (한글 원본만 사용).
     *             "ja"/"zh" 는 해당 언어 시드가 없으면 영어로 fallback 한다.
     */
    @GetMapping("/sigungu")
    public ResponseEntity<List<SigunguResponse>> getSigunguBySido(
            @RequestParam String sido,
            @RequestParam(defaultValue = "ko") String lang
    ) {
        return ResponseEntity.ok(legalDistrictService.getSigunguList(sido, lang));
    }
}
