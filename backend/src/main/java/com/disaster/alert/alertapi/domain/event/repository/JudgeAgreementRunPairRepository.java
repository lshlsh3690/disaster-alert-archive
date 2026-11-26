package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.JudgeAgreementRunPair;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JudgeAgreementRunPairRepository extends JpaRepository<JudgeAgreementRunPair, Long> {

    /** 측정 하나의 쌍별 판정 전부. 272쌍 셋이면 수백 행이다. */
    List<JudgeAgreementRunPair> findByRunId(long runId);
}
