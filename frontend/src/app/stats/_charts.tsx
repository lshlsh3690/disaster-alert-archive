"use client";

/**
 * _charts.tsx
 *
 * 모든 차트 파일에서 공통으로 가져다 쓰는 기본 UI 컴포넌트 모음입니다.
 * 현재는 "데이터 없음"과 "로딩 중" 두 가지 상태만 담당합니다.
 *
 * 왜 따로 파일로 분리했나요?
 *   - DonutChart, HeatmapChart, WeatherChart 등 여러 파일에서 동시에 import 합니다.
 *   - 한 곳에 모아두면 스타일을 바꿀 때 여기서만 수정하면 됩니다.
 */

import { useI18n } from "@/hooks/useI18n";

/**
 * EmptyChart
 *
 * API 응답 데이터가 빈 배열([])일 때 차트 자리에 보여주는 컴포넌트입니다.
 * props(입력값)가 없으므로 <EmptyChart /> 처럼 단순하게 사용합니다.
 */
export function EmptyChart() {
  const t = useI18n();
  return (
    // flex-1: 부모 컨테이너의 남은 공간을 모두 차지합니다
    // items-center justify-center: 가로·세로 모두 가운데 정렬
    <div className="flex-1 flex items-center justify-center text-sm text-gray-400">
      {t.statsPage.noData}
    </div>
  );
}

/**
 * LoadingChart
 *
 * React Query가 서버에서 데이터를 가져오는 동안 차트 자리에 보여주는 스피너입니다.
 * border-t-blue-500: 상단 테두리만 파란색 → CSS 애니메이션과 합쳐져 회전 효과를 냅니다.
 */
export function LoadingChart() {
  return (
    <div className="flex-1 flex items-center justify-center">
      {/* animate-spin: Tailwind가 제공하는 무한 회전 애니메이션 클래스입니다 */}
      <div className="w-6 h-6 rounded-full border-2 border-gray-200 border-t-blue-500 animate-spin" />
    </div>
  );
}
