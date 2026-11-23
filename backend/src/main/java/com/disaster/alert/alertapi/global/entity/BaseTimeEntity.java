package com.disaster.alert.alertapi.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 행을 언제 넣고 언제 고쳤는지(created_at·updated_at)를 JPA Auditing 으로 자동 기록하는 공통 부모 클래스.
 * 엔티티는 {@code extends BaseTimeEntity} 만 하면 되고, 두 시각을 직접 넣지 않는다({@code @EnableJpaAuditing} 은 BackendApplication).
 *
 * <ul>
 *   <li>created_at: JPA 로 처음 저장할 때 한 번 채워지고 바뀌지 않는다.</li>
 *   <li>updated_at: JPA 가 UPDATE 를 보낼 때마다 바뀐다. 값이 바뀌지 않아 UPDATE 가 안 나가면 그대로다.
 *       JPA 를 거치지 않고 SQL 로 넣은 행은 처음 고치기 전까지 NULL 이다.</li>
 * </ul>
 *
 * <p><b>도메인 날짜에는 쓰지 않는다.</b> 예를 들어 {@code DisasterAlert} 의 created_at 은 재난문자 발송 시각(공공 API 의 CRT_DT)이라,
 * 이 클래스를 붙이면 발송 시각이 DB 에 넣은 시각으로 덮어써진다. "행의 생성·수정 시각"인 칸에만 쓴다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
