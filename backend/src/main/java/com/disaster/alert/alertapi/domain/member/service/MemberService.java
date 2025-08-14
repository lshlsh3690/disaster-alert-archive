package com.disaster.alert.alertapi.domain.member.service;


import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.member.dto.*;
import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;
import com.disaster.alert.alertapi.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long signUp(SignUpRequest request) {
        validateEmailDuplication(request.getEmail());
        validateNicknameDuplication(request.getNickname());

        return createMember(request).getId();
    }

    @Transactional(readOnly = true)
    public Member findByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND, "이메일로 회원을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Boolean existsByEmail(String email) {
        return memberRepository.existsByEmail(email);
    }

    public MemberInfoResponse getMemberInfo(Long memberId) {
        return memberRepository.findById(memberId)
                .map(MemberInfoResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND, "해당 ID의 회원을 찾을 수 없습니다."));
    }

    @Cacheable(value = "nicknameCheck", key = "#nickname")
    public boolean isNicknameDuplicate(String nickname) {
        boolean existsByNickname = memberRepository.existsByNickname(nickname);
        log.info("Nickname {} exists: {}", nickname, existsByNickname);
        if (existsByNickname){
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }
        return false;
    }

    @Transactional
    public Member createMember(SignUpRequest request) {
        Member member = Member.create(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getNickname(),
                MemberRole.USER
        );

        return memberRepository.save(member);
    }

    @Transactional
    public MemberResponse updateMember(Long id, MemberUpdateRequest request) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND, "해당 ID의 회원을 찾을 수 없습니다."));

        // 닉네임이 기존과 다르면 중복 검사
        if (!member.getNickname().equals(request.nickname()) &&
                isNicknameDuplicate(request.nickname())) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }
        member.changeInfo(request.nickname());

        return MemberResponse.from(member);
    }

    @Transactional
    public void deleteMember(Long id) {
        if (!memberRepository.existsById(id)) {
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND, "해당 ID의 회원을 찾을 수 없습니다.");
        }
        Member member = memberRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND, "해당 ID의 회원을 찾을 수 없습니다."));

        member.delete();
    }

    private void validateEmailDuplication(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    private void validateNicknameDuplication(String nickname) {
        if (memberRepository.existsByNickname(nickname)) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }
}
