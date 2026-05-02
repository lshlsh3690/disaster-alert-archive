package com.disaster.alert.alertapi.domain.legaldistrict.controller;

import com.disaster.alert.alertapi.domain.legaldistrict.repository.LegalDistrictRepository;
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

    private final LegalDistrictRepository legalDistrictRepository;

    @GetMapping("/sigungu")
    public ResponseEntity<List<String>> getSigunguBySido(@RequestParam String sido) {
        return ResponseEntity.ok(legalDistrictRepository.findSigunguBySido(sido));
    }
}
