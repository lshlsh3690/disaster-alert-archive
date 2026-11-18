package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.model.AgencyAlias;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgencyAliasRepository extends JpaRepository<AgencyAlias, String> {
}
