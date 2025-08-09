package com.disaster.alert.alertapi.domain.member.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
// JPA에서 isDeleted가 false인 데이터만 조회하도록 설정, 조회 쿼리에만 적용
// querydsl에서 @Where은 적용되지 않음
@Where(clause = "is_deleted = false")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;  // 로그인 ID

    @Column(nullable = false)
    private String password;  // 암호화된 비밀번호

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role; // USER, ADMIN 등 권한

    @Column(columnDefinition = "boolean default false")
    private boolean isDeleted = false;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public static Member create(String email, String password, String nickname, MemberRole role) {
        return Member.builder()
                .email(email)
                .password(password)
                .nickname(nickname)
                .role(role)
                .build();
    }

    public void changeInfo(String nickname) {
        this.nickname = nickname;
    }
    public void delete() {
        this.isDeleted = true;
    }
}
