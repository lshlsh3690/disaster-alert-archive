package com.disaster.alert.alertapi.domain.disasteralert.dto;

import com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlert;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class UserAlertDtos {
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        @NotBlank @Size(max = 120)
        private String title;

        @NotBlank @Size(max = 2000)
        private String message;

        @Size(max = 50)
        private String disasterType;               // 예: "호우", "지진" 등 (선택)

        // 사용자가 텍스트로 보낼 수도 있으니 enumName 또는 description 둘 다 허용하려면 서비스에서 매핑
        private String disasterLevel;             // 예: "LEVEL_3" 또는 "위급재난" (선택)

        @NotNull
        private LocalDateTime occurredAt;

        @NotNull @Size(min = 1)
        private List<@NotBlank @Size(max = 100) String> regionCodes; // 예: ["1111010200", ...] (최소 1개)
        @NotNull @Size(min = 1)
        private List<@NotBlank @Size(max = 100) String> regionNames; //  예: ["서울특별시 종로구 신교동", ...] (최소 1개)
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRequest {
        @Size(max = 120)
        private String title;

        @Size(max = 2000)
        private String message;

        @Size(max = 50)
        private String disasterType;

        private String disasterLevel;

        private LocalDateTime occurredAt;

        private List<@NotBlank @Size(max = 100) String> regionCodes;
        private List<@NotBlank @Size(max = 100) String> regionNames;
    }

    @Getter @Builder
    public static class Response {
        private Long id;
        private Long createdById;
        private String createdByNickname;
        private String title;
        private String message;
        private String disasterType;
        private String emergencyLevelText;   // "안전안내/긴급재난/위급재난" 등 설명
        private String emergencyLevel;       // "LEVEL_1/2/3" (프론트에서 선택 용이)
        private LocalDateTime occurredAt;
        private LocalDateTime createdAt;
        private LocalDateTime modifiedAt;
        private List<String> regionCodes;
        private List<String> regionNames;

        public static Response from(UserDisasterAlert u) {
            return fromWithNickname(u, null);
        }

        public static Response fromWithNickname(UserDisasterAlert u, String nickname) {
            return Response.builder()
                    .id(u.getId())
                    .createdById(u.getCreatedById())
                    .createdByNickname(nickname)
                    .title(u.getTitle())
                    .message(u.getMessage())
                    .disasterType(u.getDisasterType())
                    .emergencyLevel(u.getDisasterLevel() != null ? u.getDisasterLevel().name() : null)
                    .emergencyLevelText(u.getDisasterLevel() != null ? u.getDisasterLevel().getDescription() : null)
                    .occurredAt(u.getOccurredAt())
                    .createdAt(u.getCreatedAt())
                    .modifiedAt(u.getModifiedAt())
                    .regionNames(u.getRegions().stream()
                            .map(r -> r.getLegalDistrict().getName())
                            .toList())
                    .regionCodes(u.getRegions().stream()
                            .map(r -> r.getLegalDistrict().getCode())
                            .toList())
                    .build();
        }
    }
}
