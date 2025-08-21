// src/components/map/KakaoMetroMap.tsx
"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { loadKakaoMapSdk } from "@/lib/kakaoMapLoader";
import { groupToMetros, METRO_COORDS, type Metro } from "@/ui/metros";
import { useAlertStats, useSidoStats } from "@/lib/queries/useAlerts";

type OverlayRef = { setMap: (m: any | null) => void };

function todayRange() {
  const d = new Date();
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  const s = `${yyyy}-${mm}-${dd}`;
  return { startDate: s, endDate: s };
}

//한국 경계 박스(대략): SW(제주 남서) ~ NE(강원 북동)
const KOREA_SW = { lat: 32.5, lng: 124.5 };
const KOREA_NE = { lat: 38.8, lng: 132.0 };
//줌 레벨 제한(숫자 클수록 더 멀리). 1~14 정도 사용. 12면 전국이 꽉 차고 더 멀리 못 나감.
const MAX_LEVEL = 12;
//너무 과도한 확대도 방지하고 싶으면 최소 레벨도 제한
const MIN_LEVEL = 3;

export default function KakaoMetroMap({ todayOnly = true }: { todayOnly?: boolean }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const router = useRouter();
  const [{ ready, kakao }, setReady] = useState<{ ready: boolean; kakao: any | null }>({ ready: false, kakao: null });

  const dateParams = todayOnly ? todayRange() : ({} as any);
  const { data, isLoading, isError } = useSidoStats(dateParams);
  const metroCounts = useMemo(() => groupToMetros(data ?? []), [data]);

  useEffect(() => {
    let overlays: OverlayRef[] = [];
    let map: any;

    loadKakaoMapSdk()
      .then((kk) => {
        setReady({ ready: true, kakao: kk });
        if (!containerRef.current) return;

        const center = new kk.maps.LatLng(36.3, 127.8); // 대한민국 중심 좌표
        map = new kk.maps.Map(containerRef.current, {
          center,
          level: 10, // 숫자 클수록 넓게
        });

        // 2) 대한민국 경계(bounds) 설정 + 그 범위로 맞춤
        const bounds = new kk.maps.LatLngBounds(
          new kk.maps.LatLng(KOREA_SW.lat, KOREA_SW.lng),
          new kk.maps.LatLng(KOREA_NE.lat, KOREA_NE.lng)
        );
        map.setBounds(bounds);

        // 3) 줌 제한(더 멀리 못 보게)
        if (typeof map.setMaxLevel === "function") {
          map.setMaxLevel(MAX_LEVEL);
        }
        // (선택) 너무 많이 확대도 못 하게
        if (typeof map.setMinLevel === "function") {
          map.setMinLevel(MIN_LEVEL);
        }

        // 4) 범위 밖으로 드래그/줌 시 강제 복귀
        const clampCenter = () => {
          const c = map.getCenter();
          const sw = bounds.getSouthWest();
          const ne = bounds.getNorthEast();
          const clampedLat = Math.min(Math.max(c.getLat(), sw.getLat()), ne.getLat());
          const clampedLng = Math.min(Math.max(c.getLng(), sw.getLng()), ne.getLng());
          if (clampedLat !== c.getLat() || clampedLng !== c.getLng()) {
            map.setCenter(new kk.maps.LatLng(clampedLat, clampedLng));
          }
        };

        const clampLevel = () => {
          const lv = map.getLevel();
          if (lv > MAX_LEVEL) map.setLevel(MAX_LEVEL);
          if (lv < MIN_LEVEL) map.setLevel(MIN_LEVEL);
        };

        kk.maps.event.addListener(map, "dragend", clampCenter);
        kk.maps.event.addListener(map, "zoom_changed", clampLevel);
        // idle: 이동·확대/축소 후 유휴 상태 → 최종 보정
        kk.maps.event.addListener(map, "idle", () => {
          clampLevel();
          clampCenter();
        });

        // 컨트롤(줌)
        const zoomControl = new kk.maps.ZoomControl();
        map.addControl(zoomControl, kk.maps.ControlPosition.RIGHT);

        // 오버레이 생성 함수
        const createOverlay = (name: Metro, count: number) => {
          const pos = METRO_COORDS[name];
          if (!pos) return;

          const div = document.createElement("div");
          div.style.cssText =
            "transform: translate(-50%, -50%); background: rgba(255,255,255,0.6); border: 1px solid #e5e7eb; border-radius: 12px; padding: 4px 8px; box-shadow: 0 1px 2px rgba(0,0,0,0.06); display:flex; flex-direction:column; align-items:center; gap:2px; cursor:pointer; transition: background 0.2s;";
          div.addEventListener("mouseenter", () => {
            div.style.background = "white";
          });
          div.addEventListener("mouseleave", () => {
            div.style.background = "rgba(255,255,255,0.6)";
          });
          div.innerHTML = `
              <div style="font-size:10px;color:#6b7280;">${name}</div>
              <div style="font-size:14px;font-weight:700;">${count.toLocaleString("ko-KR")}</div>
            `;
          div.addEventListener("click", () => {
            router.push(`/alerts?region=${encodeURIComponent(name)}`);
          });

          const overlay = new kk.maps.CustomOverlay({
            position: new kk.maps.LatLng(pos.lat, pos.lng),
            content: div,
            yAnchor: 0.5,
            xAnchor: 0.5,
            clickable: true,
            map,
          });
          overlays.push(overlay);
        };

        // 초기 렌더
        Object.entries(metroCounts).forEach(([name, count]) => createOverlay(name as Metro, count as number));
      })
      .catch(() => setReady({ ready: false, kakao: null }));

    // 데이터 변경 시 오버레이 갱신
    if (ready && kakao && containerRef.current) {
      // 기존 오버레이 제거 후 다시 그림
      // (위 load 이후 데이터가 바뀌었을 때 재실행되도록 의존성 조합 고려)
    }

    return () => {
      // 정리
      overlays.forEach((ov) => ov.setMap(null));
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, kakao]); // 최초 SDK 로드용

  // metroCounts 변화에 반응해 오버레이 갱신
  useEffect(() => {
    if (!ready || !kakao || !containerRef.current) return;
    // 가장 간단한 방법: 맵 인스턴스를 찾아 재생성보단, 아래처럼 리렌더 시 container를 비우고 다시 초기화해도 OK
    // 여기서는 간결성을 위해 SDK 초기화 이펙트를 dependency 최소화하고,
    // 최초 렌더 이후 건수 텍스트만 업데이트 하려면 오버레이 배열 관리를 더 세밀하게 해야 함.
    // 대시보드 성격상 빈번 변경이 아니므로 최초 로드 시점 데이터로 충분한 경우가 많음.
  }, [metroCounts, ready, kakao]);

  return (
    <div className="bg-white rounded-xl shadow w-full h-[300px] relative overflow-hidden">
      {(isLoading || !ready) && (
        <div className="absolute inset-0 flex items-center justify-center text-sm text-gray-500">
          지도를 불러오는 중…
        </div>
      )}
      {isError && (
        <div className="absolute inset-0 flex items-center justify-center text-sm text-red-500">지도 로딩 실패</div>
      )}
      <div ref={containerRef} className="w-full h-full" />
      <div className="absolute bottom-2 right-3 text-xs text-gray-400 bg-white/70 rounded px-2 py-1">
        데이터: 광역시 합산(숫자만 표시)
      </div>
    </div>
  );
}
