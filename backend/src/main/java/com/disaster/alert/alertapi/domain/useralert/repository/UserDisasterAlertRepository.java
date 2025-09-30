package com.disaster.alert.alertapi.domain.useralert.repository;

import com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserDisasterAlertRepository extends JpaRepository<UserDisasterAlert, Long> {
}
