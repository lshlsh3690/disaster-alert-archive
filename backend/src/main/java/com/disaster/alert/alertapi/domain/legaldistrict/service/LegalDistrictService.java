package com.disaster.alert.alertapi.domain.legaldistrict.service;

import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import com.disaster.alert.alertapi.domain.legaldistrict.repository.LegalDistrictRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LegalDistrictService {
    private final LegalDistrictRepository legalDistrictRepository;
    /**
     * 법정동 데이터를 초기화하는 메서드입니다.
     * 이 메서드는 애플리케이션 시작 시 호출되어 법정동 데이터를 초기화합니다.
     */
    public void saveAllLegalDistricts() {
        try(InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("data/legal_district_init_file.csv");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))){

            String line;

            // 첫 번째 줄은 헤더이므로 건너뜁니다.
            reader.readLine();

            List<LegalDistrict> districts = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1); // 빈 값도 포함되도록

                if (parts.length < 3) continue; // 잘못된 라인 건너뛰기

                String code = parts[0].trim();
                String name = parts[1].trim();
                String isActiveStr = parts[2].trim();

                if (legalDistrictRepository.existsByCode(code)) {
                    continue;
                }

                boolean isActive = "존재".equals(isActiveStr);

                districts.add(LegalDistrict.builder()
                        .code(code)
                        .name(name)
                        .isActive(isActive)
                        .isActiveString(isActiveStr)
                        .build());
            }
            legalDistrictRepository.saveAll(districts);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
