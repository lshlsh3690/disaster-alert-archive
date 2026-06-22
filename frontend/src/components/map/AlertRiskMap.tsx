"use client";

import { useEffect, useRef, useState } from "react";
import { loadKakaoMapSdk } from "@/lib/kakaoMapLoader";
import { toLatLngPaths, calcBounds, largestRingCenter } from "@/lib/kakaoGeo";
import { fetchGeoJsonCached } from "@/lib/geojsonCache";
import { normalizeScore, scoreToGrade, aggregateBySigungu, type ImpactGrade } from "@/lib/riskScore";
import { useI18n } from "@/hooks/useI18n";
import { useLanguageStore } from "@/store/languageStore";
import type { RegionImpact } from "@/types/risk";

/** Polygon | MultiPolygon geometry (kakaoGeo 헬퍼가 둘 다 처리). */
type GeoGeometry = { type: "Polygon" | "MultiPolygon"; coordinates: number[][][] | number[][][][] };

interface SigunguFeature {
  type: "Feature";
  geometry: GeoGeometry;
  properties: { SIG_CD: string; SIG_KOR_NM: string; SIG_ENG_NM: string };
}
interface SidoFeature {
  type: "Feature";
  geometry: GeoGeometry;
  properties: { CTPRVN_CD: string; CTP_KOR_NM: string; CTP_ENG_NM: string };
}

// 등급 계산은 @/lib/riskScore, 등급 표시명은 i18n(t.risk.map.grade)에서 가져옴
const GRADE_TEXT = ["#15803d", "#a16207", "#c2410c", "#b91c1c"] as const;

// KakaoPolygonMap 의 위험도 팔레트(1~4단계)와 동일 계열 유지
const GRADE_POLY = [
  { fillColor: "#86efac", fillOpacity: 0.45, strokeColor: "#16a34a", strokeOpacity: 0.8, strokeWeight: 1 },
  { fillColor: "#fde047", fillOpacity: 0.50, strokeColor: "#ca8a04", strokeOpacity: 0.9, strokeWeight: 1 },
  { fillColor: "#fb923c", fillOpacity: 0.60, strokeColor: "#ea580c", strokeOpacity: 0.9, strokeWeight: 1 },
  { fillColor: "#f87171", fillOpacity: 0.70, strokeColor: "#dc2626", strokeOpacity: 1.0, strokeWeight: 1.5 },
] as const;

const GRADE_POLY_HOVER = [
  { fillColor: "#4ade80", fillOpacity: 0.65 },
  { fillColor: "#facc15", fillOpacity: 0.70 },
  { fillColor: "#f97316", fillOpacity: 0.80 },
  { fillColor: "#ef4444", fillOpacity: 0.85 },
] as const;

/** 영향권 밖 시도 배경 스타일 (컨텍스트용, 인터랙션 없음). */
const SIDO_BACKDROP = { fillColor: "#f3f4f6", fillOpacity: 0.5, strokeColor: "#9ca3af", strokeOpacity: 0.6, strokeWeight: 1 };

interface Props {
  impacts: RegionImpact[];
  mapHeight?: string;
}

/**
 * 재난문자 상세 페이지용 영향 지역 히트맵.
 * 영향받은 시군구 폴리곤을 영향 등급별 색상으로 칠하고, 그 외 지역은 회색 배경으로 표시.
 */
export default function AlertRiskMap({ impacts, mapHeight = "420px" }: Props) {
  const t = useI18n();
  const language = useLanguageStore((s) => s.language);
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef       = useRef<any>(null);
  const kakaoRef     = useRef<any>(null);

  const sigunguDataRef = useRef<SigunguFeature[]>([]);
  const sidoDataRef    = useRef<SidoFeature[]>([]);
  const overlaysRef    = useRef<any[]>([]);

  const [mapReady,  setMapReady]  = useState(false);
  const [status,    setStatus]    = useState<"loading" | "ready" | "error">("loading");
  const [hoverInfo, setHoverInfo] = useState<{ name: string; score: number; grade: ImpactGrade } | null>(null);
  const [mousePos,  setMousePos]  = useState<{ x: number; y: number } | null>(null);
  const [unmatched, setUnmatched] = useState<string[]>([]);

  /* ── 지도 초기화 (1회) ── */
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      loadKakaoMapSdk(),
      fetchGeoJsonCached("/sigungu.geojson"),
      fetchGeoJsonCached("/sido.geojson"),
    ]).then(([kakao, sigunguGeo, sidoGeo]) => {
      if (cancelled || !containerRef.current) return;
      kakaoRef.current       = kakao;
      sigunguDataRef.current = sigunguGeo.features;
      sidoDataRef.current    = sidoGeo.features;
      mapRef.current = new kakao.maps.Map(containerRef.current, {
        center: new kakao.maps.LatLng(36.2, 127.7),
        level: 12,
      });
      mapRef.current.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.RIGHT);
      setMapReady(true);
      setStatus("ready");
    }).catch(() => setStatus("error"));
    return () => { cancelled = true; };
  }, []);

  /* ── 영향 지역 폴리곤 렌더 ── */
  useEffect(() => {
    const kakao = kakaoRef.current;
    const map   = mapRef.current;
    if (!mapReady || !kakao || !map) return;

    // 기존 오버레이 제거
    overlaysRef.current.forEach((o) => o.setMap(null));
    overlaysRef.current = [];
    setHoverInfo(null);

    const scoreMap = aggregateBySigungu(impacts);
    if (scoreMap.size === 0) return;

    // 1) 전국 시도 배경 (컨텍스트)
    sidoDataRef.current.forEach((f) => {
      toLatLngPaths(kakao, f.geometry).forEach((path) => {
        const p = new kakao.maps.Polygon({ map, path, ...SIDO_BACKDROP, zIndex: 1 });
        overlaysRef.current.push(p);
      });
    });

    // 2) 영향받은 시군구 히트맵 폴리곤
    const bounds = new kakao.maps.LatLngBounds();
    const missing: string[] = [];
    let matchedAny = false;

    scoreMap.forEach((score, sigCd) => {
      // 시군구 매칭 → 실패 시 시도 전체 발송 코드(xx000, 예: 29000 광주광역시) 폴백
      let geometry: GeoGeometry | null = null;
      let korName = "", engName = "";
      const sig = sigunguDataRef.current.find((f) => f.properties.SIG_CD === sigCd);
      if (sig) {
        geometry = sig.geometry;
        korName  = sig.properties.SIG_KOR_NM;
        engName  = sig.properties.SIG_ENG_NM;
      } else if (sigCd.endsWith("000")) {
        const sido = sidoDataRef.current.find((f) => f.properties.CTPRVN_CD === sigCd.slice(0, 2));
        if (sido) {
          geometry = sido.geometry;
          korName  = sido.properties.CTP_KOR_NM;
          engName  = sido.properties.CTP_ENG_NM;
        }
      }
      if (!geometry) { missing.push(sigCd); return; }
      matchedAny = true;

      // 지도 레이블/툴팁용 지역명 — 영어 UI는 geojson 의 영문명 사용 (ja/zh 는 미보유 → 한국어)
      const name   = language === "en" ? engName || korName : korName;
      const grade  = scoreToGrade(score);
      const style  = GRADE_POLY[grade];
      const hStyle = { ...GRADE_POLY[grade], ...GRADE_POLY_HOVER[grade] };

      // 시군구 이름 레이블
      const [cLat, cLng] = largestRingCenter(geometry);
      const labelDiv = document.createElement("div");
      labelDiv.style.cssText =
        "font-size:11px;font-weight:700;color:#1f2937;pointer-events:none;white-space:nowrap;" +
        "text-shadow:0 0 3px white,0 0 3px white,0 0 3px white;";
      labelDiv.textContent = name;
      overlaysRef.current.push(new kakao.maps.CustomOverlay({
        map, position: new kakao.maps.LatLng(cLat, cLng),
        content: labelDiv, yAnchor: 0.5, xAnchor: 0.5, zIndex: 3,
      }));

      toLatLngPaths(kakao, geometry).forEach((path) => {
        const p = new kakao.maps.Polygon({ map, path, ...style, zIndex: 2 });
        kakao.maps.event.addListener(p, "mouseover", () => {
          p.setOptions(hStyle);
          setHoverInfo({ name, score, grade });
        });
        kakao.maps.event.addListener(p, "mouseout", () => {
          p.setOptions(style);
          setHoverInfo(null);
        });
        overlaysRef.current.push(p);
      });
      // 영향 지역 전체가 화면에 들어오도록 bounds 확장
      const b = calcBounds(kakao, geometry);
      bounds.extend(b.getSouthWest());
      bounds.extend(b.getNorthEast());
    });

    setUnmatched(missing);
    if (matchedAny) map.setBounds(bounds, 60, 60, 60, 60);
    // language: 지역명 레이블이 언어를 따라가므로 변경 시 재렌더 필요
  }, [mapReady, impacts, language]);

  if (status === "error") {
    return (
      <div className="h-40 flex items-center justify-center rounded-lg border border-gray-200 bg-gray-50 text-sm text-gray-500">
        {t.risk.map.error}
      </div>
    );
  }

  return (
    <div
      className="relative rounded-lg border border-gray-200 overflow-hidden"
      style={{ height: mapHeight }}
      onMouseMove={(e) => {
        const rect = e.currentTarget.getBoundingClientRect();
        setMousePos({ x: e.clientX - rect.left, y: e.clientY - rect.top });
      }}
      onMouseLeave={() => { setHoverInfo(null); setMousePos(null); }}
    >
      <div ref={containerRef} className="absolute inset-0" />

      {status === "loading" && (
        <div className="absolute inset-0 flex items-center justify-center bg-gray-50 text-sm text-gray-500">
          {t.risk.map.loading}
        </div>
      )}

      {/* 등급 범례 */}
      {status === "ready" && (
        <div className="absolute bottom-3 left-3 z-10 bg-white/90 rounded-lg shadow px-3 py-2">
          <p className="text-[11px] font-semibold text-gray-500 mb-1">{t.risk.map.legend}</p>
          <div className="flex items-center gap-2">
            {t.risk.map.grade.map((label, lv) => (
              <div key={label} className="flex items-center gap-1">
                <span
                  className="inline-block w-3 h-3 rounded-sm border"
                  style={{ background: GRADE_POLY[lv].fillColor, borderColor: GRADE_POLY[lv].strokeColor }}
                />
                <span className="text-[11px] text-gray-600">{label}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 지오메트리 매칭 실패 안내 (드물지만 코드 개편 등으로 발생 가능) */}
      {unmatched.length > 0 && (
        <div className="absolute top-3 left-3 z-10 bg-amber-50/95 border border-amber-200 rounded-lg px-3 py-1.5 text-[11px] text-amber-700">
          {t.risk.map.unmatched}
        </div>
      )}

      {/* 호버 툴팁 */}
      {hoverInfo && mousePos && (
        <div
          className="absolute pointer-events-none z-20 bg-white border border-gray-200 rounded-lg px-3 py-2 shadow"
          style={{ left: mousePos.x + 14, top: mousePos.y - 10, transform: "translateY(-100%)", whiteSpace: "nowrap" }}
        >
          <div className="text-[13px] font-bold text-gray-900">{hoverInfo.name}</div>
          <div className="flex items-center justify-between gap-3 mt-0.5">
            <span className="text-[11px] text-gray-500">{t.risk.map.legend}</span>
            <span className="text-xs font-bold" style={{ color: GRADE_TEXT[hoverInfo.grade] }}>
              {t.risk.map.grade[hoverInfo.grade]}
            </span>
          </div>
          <div className="flex items-center justify-between gap-3">
            <span className="text-[11px] text-gray-500">{t.risk.map.score}</span>
            <span className="text-[11px] font-semibold text-gray-700">
              {Math.round(normalizeScore(hoverInfo.score) * 100)}%
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
