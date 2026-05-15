package com.disaster.alert.alertapi.domain.legaldistrict.dto;

/**
 * 시/군/구 드롭다운 응답 DTO.
 *
 * <p><b>왜 객체 배열로 응답하는가?</b>
 * 다국어 응답을 위해 "한글 원본" + "번역명" 을 함께 내려야 한다.
 * <ul>
 *   <li>{@code name} — 한글 원본. 프론트가 폼 제출 시 백엔드 검색 키로 사용한다 (검색은 한글 기반).</li>
 *   <li>{@code translatedName} — 화면 표시용 번역명. 한국어/미지원 lang 요청 시 null.</li>
 * </ul>
 *
 * <p><b>fallback 정책</b> ({@code LegalDistrictTranslationService.getTranslatedNamesWithEnglishFallback} 사용)
 * <ul>
 *   <li>한국어/미지원 lang → translatedName 은 null. 프론트는 {@code translatedName ?? name} 로 처리.</li>
 *   <li>일본어/중국어 시드가 없는 코드 → 영어로 fallback.</li>
 *   <li>영어도 없는 코드 → null (프론트에서 한글 원본으로 fallback).</li>
 * </ul>
 *
 * <p><b>예시 응답 (lang=en)</b>
 * <pre>
 * [
 *   { "name": "강남구", "translatedName": "Gangnam-gu" },
 *   { "name": "강동구", "translatedName": "Gangdong-gu" }
 * ]
 * </pre>
 */
public record SigunguResponse(String name, String translatedName, String code) {
}
