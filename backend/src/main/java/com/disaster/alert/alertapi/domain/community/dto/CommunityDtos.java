package com.disaster.alert.alertapi.domain.community.dto;

import com.disaster.alert.alertapi.domain.community.model.CommunityPost;
import com.disaster.alert.alertapi.domain.community.model.CommunityPostCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

public class CommunityDtos {
    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        @NotNull private CommunityPostCategory category; // NOTICE or FREE
        @NotBlank @Size(max=120) private String title;
        @NotBlank @Size(max=4000) private String content;
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long id;
        private CommunityPostCategory category;
        private Long authorId;
        private String authorNickname;
        private String title;
        private String content;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(CommunityPost p) {
            return Response.builder()
                    .id(p.getId())
                    .category(p.getCategory())
                    .authorId(p.getAuthor() != null ? p.getAuthor().getId() : null)
                    .authorNickname(p.getAuthor() != null ? p.getAuthor().getNickname() : null)
                    .title(p.getTitle())
                    .content(p.getContent())
                    .createdAt(p.getCreatedAt())
                    .updatedAt(p.getUpdatedAt())
                    .build();
        }
    }
}


