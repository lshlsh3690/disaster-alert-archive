package com.disaster.alert.alertapi.domain.member.controller;

import com.disaster.alert.alertapi.global.dto.ApiResponse;
import com.disaster.alert.alertapi.domain.member.dto.MemberInfoResponse;
import com.disaster.alert.alertapi.domain.member.dto.MemberResponse;
import com.disaster.alert.alertapi.domain.member.dto.MemberUpdateRequest;
import com.disaster.alert.alertapi.domain.member.service.MemberDetails;
import com.disaster.alert.alertapi.domain.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
public class MemberController {
    private final MemberService memberService;

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/me")
    public ResponseEntity<MemberInfoResponse> getMyInfo(@AuthenticationPrincipal MemberDetails memberDetails) {
        MemberInfoResponse response = memberService.getMemberInfo(memberDetails.member().getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/check-nickname")
    public ResponseEntity<ApiResponse<Void>> checkNickname(@RequestParam String nickname) {
        memberService.isNicknameDuplicate(nickname);
        return ResponseEntity.ok(
                ApiResponse.success( "사용 가능한 닉네임입니다.", null)
        );
    }

    @PreAuthorize("#id == principal.id or hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<MemberResponse> update(@PathVariable Long id,
                                                 @RequestBody @Valid MemberUpdateRequest request) {
        MemberResponse response = memberService.updateMember(id, request);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("#id == principal.id or hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        memberService.deleteMember(id);
        return ResponseEntity.noContent().build();
    }
}
