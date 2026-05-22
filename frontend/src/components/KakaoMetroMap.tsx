"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { loadKakaoMapSdk } from "@/lib/kakaoMapLoader";
import { groupToMetros, METRO_COORDS, type Metro } from "@/ui/metros";
import { useSidoStats } from "@/lib/queries/useAlerts";

type OverlayRef = { setMap: (m: any | null) => void };

function todayRange() {
  const d = new Date();
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  const s = `${yyyy}-${mm}-${dd}`;
  return { startDate: s, endDate: s };
}

const KOREA_SW = { lat: 32.5, lng: 124.5 };
const KOREA_NE = { lat: 38.8, lng: 132.0 };

const SIDO_ZOOM: Record<string, number> = {
  "서울특별시": 9, "부산광역시": 9, "대구광역시": 9, "인천광역시": 9,
  "광주광역시": 9, "대전광역시": 9, "울산광역시": 9, "세종특별자치시": 9,
  "경기도": 11, "강원특별자치도": 11, "충청북도": 11, "충청남도": 11,
  "전북특별자치도": 11, "전라남도": 11, "경상북도": 11, "경상남도": 11,
  "제주특별자치도": 11,
};

interface KakaoMapProps {
  todayOnly?: boolean;
  zoomable?: boolean;
  zoomLevel_MAX?: number;
  zoomLevel_MIN?: number;
  selectedSido?: string;
  sigunguStats?: Array<{ name: string; count: number }>;
  showModeLabel?: boolean;
  showOverlays?: boolean;
  mapHeight?: string;
  nationalLevel?: number;
}

export default function KakaoMetroMap({
  todayOnly = true,
  zoomable = true,
  zoomLevel_MAX = 12,
  zoomLevel_MIN = 3,
  selectedSido,
  sigunguStats,
  showModeLabel = true,
  showOverlays = true,
  mapHeight = "300px",
  nationalLevel,
}: KakaoMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const overlaysRef = useRef<OverlayRef[]>([]);
  const router = useRouter();
  const [{ ready, kakao }, setReady] = useState<{ ready: boolean; kakao: any | null }>({ ready: false, kakao: null });

  const dateParams = todayOnly ? todayRange() : ({} as any);
  const { data, isLoading, isError } = useSidoStats(dateParams);
  const metroCounts = useMemo(() => groupToMetros(data ?? []), [data]);

  // 지도 초기화
  useEffect(() => {
    loadKakaoMapSdk()
      .then((kk) => {
        setReady({ ready: true, kakao: kk });
        if (!containerRef.current) return;

        const INITIAL_LEVEL = Math.min(Math.max(zoomLevel_MIN, zoomLevel_MAX), 12);
        const center = new kk.maps.LatLng(36.3, 127.8);
        const map = new kk.maps.Map(containerRef.current, { center, level: INITIAL_LEVEL });
        mapRef.current = map;

        const bounds = new kk.maps.LatLngBounds(
          new kk.maps.LatLng(KOREA_SW.lat, KOREA_SW.lng),
          new kk.maps.LatLng(KOREA_NE.lat, KOREA_NE.lng)
        );
        map.setBounds(bounds);
        if (nationalLevel !== undefined) map.setLevel(nationalLevel);
        map.setZoomable(!!zoomable);

        if (zoomable) {
          map.setMinLevel?.(zoomLevel_MIN);
          map.setMaxLevel?.(zoomLevel_MAX);
          const zoomControl = new kk.maps.ZoomControl();
          map.addControl(zoomControl, kk.maps.ControlPosition.RIGHT);
        } else {
          const lv = map.getLevel();
          map.setMinLevel?.(lv);
          map.setMaxLevel?.(lv);
        }

        const clampCenter = () => {
          const c = map.getCenter();
          const sw = bounds.getSouthWest();
          const ne = bounds.getNorthEast();
          const lat = Math.min(Math.max(c.getLat(), sw.getLat()), ne.getLat());
          const lng = Math.min(Math.max(c.getLng(), sw.getLng()), ne.getLng());
          if (lat !== c.getLat() || lng !== c.getLng()) {
            map.setCenter(new kk.maps.LatLng(lat, lng));
          }
        };
        const clampLevel = () => {
          const lv = map.getLevel();
          const hi = zoomable ? zoomLevel_MAX : INITIAL_LEVEL;
          const lo = zoomable ? zoomLevel_MIN : INITIAL_LEVEL;
          if (lv > hi) map.setLevel(hi);
          if (lv < lo) map.setLevel(lo);
        };

        kk.maps.event.addListener(map, "dragend", clampCenter);
        if (zoomable) kk.maps.event.addListener(map, "zoom_changed", clampLevel);
        kk.maps.event.addListener(map, "idle", () => { if (zoomable) clampLevel(); clampCenter(); });
      })
      .catch(() => setReady({ ready: false, kakao: null }));

    return () => {
      overlaysRef.current.forEach((ov) => ov.setMap(null));
      overlaysRef.current = [];
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [zoomable, zoomLevel_MAX, zoomLevel_MIN, nationalLevel]);

  // 오버레이 갱신 (시도/시군구 모드 전환)
  useEffect(() => {
    if (!ready || !kakao || !mapRef.current) return;
    const map = mapRef.current;
    let cancelled = false;

    overlaysRef.current.forEach((ov) => ov.setMap(null));
    overlaysRef.current = [];

    if (!showOverlays) {
      // 지도 위치/줌만 업데이트하고 오버레이는 표시하지 않음
      if (selectedSido) {
        const sidoCoords = METRO_COORDS[selectedSido as Metro];
        if (sidoCoords) {
          map.setMinLevel?.(1);
          map.setMaxLevel?.(14);
          map.setCenter(new kakao.maps.LatLng(sidoCoords.lat, sidoCoords.lng));
          map.setLevel(SIDO_ZOOM[selectedSido] ?? 9);
          if (!zoomable) {
            const lv = map.getLevel();
            map.setMinLevel?.(lv);
            map.setMaxLevel?.(lv);
          }
        }
      } else {
        const bounds = new kakao.maps.LatLngBounds(
          new kakao.maps.LatLng(KOREA_SW.lat, KOREA_SW.lng),
          new kakao.maps.LatLng(KOREA_NE.lat, KOREA_NE.lng)
        );
        map.setMinLevel?.(1);
        map.setMaxLevel?.(14);
        map.setBounds(bounds);
        if (!zoomable) {
          const lv = map.getLevel();
          map.setMinLevel?.(lv);
          map.setMaxLevel?.(lv);
        }
      }
      return () => { cancelled = true; };
    }

    if (selectedSido && sigunguStats && sigunguStats.length > 0) {
      // 시군구 모드: 선택된 시/도로 줌인
      const sidoCoords = METRO_COORDS[selectedSido as Metro];
      if (sidoCoords) {
        map.setZoomable(true);
        map.setMinLevel?.(3);
        map.setMaxLevel?.(12);
        map.setCenter(new kakao.maps.LatLng(sidoCoords.lat, sidoCoords.lng));
        map.setLevel(SIDO_ZOOM[selectedSido] ?? 9);
      }

      const geocoder = new kakao.maps.services.Geocoder();
      sigunguStats.forEach(({ name, count }) => {
        geocoder.addressSearch(`${selectedSido} ${name}`, (result: any, status: any) => {
          if (cancelled || status !== kakao.maps.services.Status.OK) return;

          const div = document.createElement("div");
          div.style.cssText =
            "transform:translate(-50%,-50%);background:rgba(255,255,255,0.85);border:1px solid #e5e7eb;border-radius:12px;padding:3px 7px;box-shadow:0 1px 3px rgba(0,0,0,0.1);display:flex;flex-direction:column;align-items:center;gap:1px;cursor:pointer;";
          div.addEventListener("mouseenter", () => { div.style.background = "white"; });
          div.addEventListener("mouseleave", () => { div.style.background = "rgba(255,255,255,0.85)"; });
          div.innerHTML = `
            <div style="font-size:9px;color:#6b7280;">${name}</div>
            <div style="font-size:12px;font-weight:700;color:#111827;">${count.toLocaleString("ko-KR")}</div>
          `;
          div.addEventListener("click", () => {
            router.push(`/alerts?sido=${encodeURIComponent(selectedSido)}&sigungu=${encodeURIComponent(name)}#list`);
          });

          const overlay = new kakao.maps.CustomOverlay({
            position: new kakao.maps.LatLng(parseFloat(result[0].y), parseFloat(result[0].x)),
            content: div,
            yAnchor: 0.5,
            xAnchor: 0.5,
            clickable: true,
            map,
          });

          if (cancelled) {
            overlay.setMap(null);
          } else {
            overlaysRef.current.push(overlay);
          }
        });
      });
    } else {
      // 시도 모드: 전국 뷰로 복귀
      const bounds = new kakao.maps.LatLngBounds(
        new kakao.maps.LatLng(KOREA_SW.lat, KOREA_SW.lng),
        new kakao.maps.LatLng(KOREA_NE.lat, KOREA_NE.lng)
      );
      // 이전 시도 선택 시 걸린 줌 제약을 해제한 뒤 setBounds로 전국 뷰 계산
      map.setMinLevel?.(1);
      map.setMaxLevel?.(14);
      map.setBounds(bounds);
      if (nationalLevel !== undefined) map.setLevel(nationalLevel);
      map.setZoomable(!!zoomable);
      if (!zoomable) {
        const lv = map.getLevel();
        map.setMinLevel?.(lv);
        map.setMaxLevel?.(lv);
      }

      Object.entries(metroCounts).forEach(([name, count]) => {
        const pos = METRO_COORDS[name as Metro];
        if (!pos) return;

        const div = document.createElement("div");
        div.style.cssText =
          "transform:translate(-50%,-50%);background:rgba(255,255,255,0.6);border:1px solid #e5e7eb;border-radius:12px;padding:4px 8px;box-shadow:0 1px 2px rgba(0,0,0,0.06);display:flex;flex-direction:column;align-items:center;gap:2px;cursor:pointer;transition:background 0.2s;";
        div.addEventListener("mouseenter", () => { div.style.background = "white"; });
        div.addEventListener("mouseleave", () => { div.style.background = "rgba(255,255,255,0.6)"; });
        div.innerHTML = `
          <div style="font-size:10px;color:#6b7280;">${name}</div>
          <div style="font-size:14px;font-weight:700;">${(count as number).toLocaleString("ko-KR")}</div>
        `;
        div.addEventListener("click", () => {
          router.push(`/alerts?sido=${encodeURIComponent(name)}#list`);
        });

        const overlay = new kakao.maps.CustomOverlay({
          position: new kakao.maps.LatLng(pos.lat, pos.lng),
          content: div,
          yAnchor: 0.5,
          xAnchor: 0.5,
          clickable: true,
          map,
        });
        overlaysRef.current.push(overlay);
      });
    }

    return () => { cancelled = true; };
  }, [ready, kakao, selectedSido, sigunguStats, metroCounts, zoomable, showOverlays, router]);

  return (
    <div className="bg-white rounded-xl shadow w-full relative overflow-hidden" style={{ height: mapHeight }}>
      {(isLoading || !ready) && (
        <div className="absolute inset-0 flex items-center justify-center text-sm text-gray-500">
          지도를 불러오는 중…
        </div>
      )}
      {isError && (
        <div className="absolute inset-0 flex items-center justify-center text-sm text-red-500">지도 로딩 실패</div>
      )}
      <div ref={containerRef} className="w-full h-full" />
      {showModeLabel && (
        <div className="absolute bottom-2 right-3 text-xs text-gray-400 bg-white/70 rounded px-2 py-1">
          {selectedSido ? `${selectedSido} 시/군/구별` : "시도별 합산"}
        </div>
      )}
    </div>
  );
}
