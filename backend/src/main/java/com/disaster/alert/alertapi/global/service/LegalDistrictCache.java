package com.disaster.alert.alertapi.global.service;

import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import com.disaster.alert.alertapi.domain.legaldistrict.repository.LegalDistrictRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class LegalDistrictCache {
    private final LegalDistrictRepository legalDistrictRepository;
    private Map<String, List<LegalDistrict>> byName;
    private Map<String, LegalDistrict> byCode;

    @PostConstruct
    public void loadCache() {
        List<LegalDistrict> all = legalDistrictRepository.findAll();
        byName = all.stream().collect(Collectors.groupingBy(LegalDistrict::getName)); // 이름 기준 그룹핑
        byCode = all.stream().collect(Collectors.toMap(LegalDistrict::getCode, it -> it, (a, b) -> a));
        log.info("법정동 캐시 초기화 완료: nameKeys={}, codeKeys={}", byName.size(), byCode.size());
    }

    public List<LegalDistrict> get(String name) {
        return byName.getOrDefault(name, List.of());
    }

    public Map<String, List<LegalDistrict>> getAll() {
        return byName;
    }

    public void refresh() {
        loadCache();
    }

    public LegalDistrict getByCode(String code) {
        return byCode.get(code);
    }

    public boolean existsCode(String code) {
        return byCode.containsKey(code);
    }
}
