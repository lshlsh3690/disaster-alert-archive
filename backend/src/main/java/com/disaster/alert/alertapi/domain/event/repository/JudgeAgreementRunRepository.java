package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.JudgeAgreementRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JudgeAgreementRunRepository extends JpaRepository<JudgeAgreementRun, Long> {

    /** 그 버전의 최신 측정 — 마친 시각이 가장 늦은 것, 같으면 id 가 큰 것(V145 인덱스와 같은 순서). 없으면 비어 있다. */
    Optional<JudgeAgreementRun> findFirstByJudgeVersionOrderByFinishedAtDescIdDesc(String judgeVersion);
}
