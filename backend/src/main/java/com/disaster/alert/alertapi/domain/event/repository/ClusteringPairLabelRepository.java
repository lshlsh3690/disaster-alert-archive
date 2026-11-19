package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.ClusteringPairLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClusteringPairLabelRepository extends JpaRepository<ClusteringPairLabel, Long> {

    /** 쌍의 라벨. alertA 는 두 알림 중 작은 id 여야 한다. */
    Optional<ClusteringPairLabel> findByAlertAAndAlertB(long alertA, long alertB);

    /**
     * alertA 가 주어진 id 중 하나인 라벨들. 불일치 쌍 한 페이지의 라벨을 한 번에 읽는 용도(쌍마다 읽으면 N+1)라,
     * alertB 까지 맞는 쌍만 고르는 일은 호출한 쪽이 한다.
     */
    List<ClusteringPairLabel> findByAlertAIn(Collection<Long> alertAs);

    /**
     * 새 라벨을 넣되, 같은 쌍이 이미 있으면(동시에 처음 저장한 다른 요청) 아무것도 하지 않고 0 을 돌려준다.
     *
     * <p>일부러 예외를 던지게 두지 않는다 — PostgreSQL 은 UNIQUE 위반이 난 트랜잭션을 통째로 중단시켜, 같은 트랜잭션에서
     * 예외를 잡고 다시 찾는 흐름이 "current transaction is aborted" 로 깨진다. 0 이면 호출한 쪽이 그 줄을 다시 찾아 고친다.
     *
     * <p>새 라벨을 만드는 곳은 여기 하나다(엔티티 생성 메서드 없음). 처음엔 만든 사람·시각과 고친 사람·시각이 같다
     * (created_by = updated_by, created_at = updated_at). 테이블에 열을 추가하면 이 SQL 도 고쳐야 한다.
     *
     * @return 넣은 줄 수(1 = 새로 저장, 0 = 이미 있었음)
     */
    @Modifying
    @Query(value = "INSERT INTO clustering_pair_label(alert_a, alert_b, label, criteria_date, created_by, created_at, "
            + "updated_by, updated_at) VALUES (:alertA, :alertB, :label, :criteriaDate, :memberId, :now, :memberId, :now) "
            + "ON CONFLICT (alert_a, alert_b) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("alertA") long alertA, @Param("alertB") long alertB, @Param("label") String label,
                       @Param("criteriaDate") LocalDate criteriaDate, @Param("memberId") long memberId,
                       @Param("now") LocalDateTime now);
}
