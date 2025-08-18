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
    private Map<String, List<LegalDistrict>> map;

    @PostConstruct
    public void loadCache() {
        List<LegalDistrict> all = legalDistrictRepository.findAll();
        map = all.stream()
                .collect(Collectors.groupingBy(LegalDistrict::getName)); // 이름 기준 그룹핑
        log.info("법정동 캐시 초기화 완료: {}건", map.size());
    }

    public List<LegalDistrict> get(String name) {
        return map.getOrDefault(name, List.of());
    }

    public Map<String, List<LegalDistrict>> getAll() {
        return map;
    }

    public void refresh() {
        loadCache();
    }
}
