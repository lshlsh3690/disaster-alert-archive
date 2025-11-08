package com.disaster.alert.alertapi.domain.member.repository;

import com.disaster.alert.alertapi.domain.member.model.MemberSocial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberSocialRepository extends JpaRepository<MemberSocial, Long> {
    Optional<MemberSocial> findByProviderAndProviderUserId(String provider, String providerUserId);
}



