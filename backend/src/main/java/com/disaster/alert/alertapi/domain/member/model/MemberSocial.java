package com.disaster.alert.alertapi.domain.member.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_social",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_provider_user", columnNames = {"provider", "provider_user_id"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class MemberSocial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_social_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(nullable = false, length = 20)
    private String provider; // google, kakao, naver

    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId; // sub/id

    @Column(name = "email_from_provider")
    private String emailFromProvider;

    @CreatedDate
    private LocalDateTime createdAt;
}



