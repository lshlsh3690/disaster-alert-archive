package com.disaster.alert.alertapi.domain.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@SuppressWarnings("unused")
public class CommentDtos {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        /** OFFICIAL | USER */
        @NotBlank
        private String source;

        @NotNull
        private Long targetId;

        @NotBlank
        @Size(max = 500)
        private String content;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String content;
        private Long authorId;
        private String authorNickname;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private boolean edited;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        @NotBlank
        @Size(max = 500)
        private String content;
    }
}


