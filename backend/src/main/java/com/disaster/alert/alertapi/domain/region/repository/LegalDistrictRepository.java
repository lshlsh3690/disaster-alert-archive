package com.disaster.alert.alertapi.domain.region.repository;

import com.disaster.alert.alertapi.domain.region.model.LegalDistrict;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LegalDistrictRepository extends JpaRepository<LegalDistrict, String> {
    Optional<LegalDistrict> findByCode(String code);

    Optional<LegalDistrict> findByName(String name);

    List<LegalDistrict> findByIsActiveTrue();

    List<LegalDistrict> findByNameInOrderByCodeAsc(List<String> names);

    boolean existsByName(String combined);
}