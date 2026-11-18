package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.ClusteringPairLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClusteringPairLabelRepository extends JpaRepository<ClusteringPairLabel, Long> {

    /** 쌍의 라벨. alertA 는 두 알림 중 작은 id 여야 한다. */
    Optional<ClusteringPairLabel> findByAlertAAndAlertB(long alertA, long alertB);
}
