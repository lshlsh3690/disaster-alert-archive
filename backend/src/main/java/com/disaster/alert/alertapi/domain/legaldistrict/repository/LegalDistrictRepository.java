package com.disaster.alert.alertapi.domain.legaldistrict.repository;

import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LegalDistrictRepository extends JpaRepository<LegalDistrict, String> {
    Optional<LegalDistrict> findByCode(String code);

    Optional<LegalDistrict> findByName(String name);

    List<LegalDistrict> findAllByName(String name);

    List<LegalDistrict> findByIsActiveTrue();

    List<LegalDistrict> findByNameInOrderByCodeAsc(List<String> names);

    boolean existsByName(String combined);

    boolean existsByCode(String code);

    @Query(value = """
            SELECT DISTINCT SPLIT_PART(name, ' ', 2)
            FROM legal_district
            WHERE name LIKE CONCAT(:sido, ' %')
              AND is_active = true
              AND SPLIT_PART(name, ' ', 2) != ''
            ORDER BY 1
            """, nativeQuery = true)
    List<String> findSigunguBySido(@Param("sido") String sido);
}