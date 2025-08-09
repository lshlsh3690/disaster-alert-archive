package com.disaster.alert.alertapi.domain.auth.dto;

public record   EmailCodeVerificationRequest(String email, String code) {}

