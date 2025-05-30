package com.disaster.alert.alertapi.domain.disasteralert.model;

import java.util.Arrays;

public enum DisasterLevel
{
    LEVEL_1("안전안내"),
    LEVEL_2("긴급재난"),
    LEVEL_3("위급재난"),

    ;

    private final String description;

    DisasterLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static DisasterLevel fromDescription(String description) {
        return Arrays.stream(values())
                .filter(level -> level.description.equals(description))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown disaster level: " + description));
    }
}