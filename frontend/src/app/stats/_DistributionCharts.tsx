"use client";

/**
 * _DistributionCharts.tsx
 *
 * "지금 현재의 분포"를 보여주는 차트 컴포넌트 모음입니다.
 * 시간 축 없이 비율·건수를 한 눈에 보여주는 것이 특징입니다.
 *
 * 포함된 컴포넌트:
 *   - DonutChart     : 유형/지역별 점유율 도넛(파이) 차트
 *   - HorizontalBar  : 가로 막대 차트 (지역 랭킹 등)
 *   - VerticalBar    : 세로 막대 차트 (유형 랭킹 등)
 *   - LevelsCard     : 재난 경보 단계(안전·긴급·위급) 카드 + 게이지
 */

import {
  BarChart, Bar, Cell,       // 막대 차트 관련 컴포넌트
  XAxis, YAxis,              // 가로·세로 축
  Tooltip,                   // 마우스 호버 시 나타나는 팝업
  ResponsiveContainer,       // 부모 크기에 맞춰 차트를 자동 리사이즈
} from "recharts";
import { TYPE_COLORS, BAR_COLORS, CHART_COLORS, getLevelMeta } from "./_constants";
import type { LevelStat, RegionStat } from "./_constants";
import { EmptyChart } from "./_charts";
import { useI18n } from "@/hooks/useI18n";
import { useLanguageStore } from "@/store/languageStore";
import { formatMessage } from "@/utils/formatMessage";

const LANG_LOCALE: Record<string, string> = { ko: "ko-KR", en: "en-US", zh: "zh-CN", ja: "ja-JP" };

// ─── 공통 툴팁 스타일 ────────────────────────────────────────────────────────
//
// Recharts Tooltip은 content prop에 커스텀 컴포넌트를 넘겨서 디자인을 바꿀 수 있습니다.
// 아래 스타일 객체들은 모든 툴팁에서 동일하게 사용합니다.

// 툴팁 박스 배경·모서리 둥글기 등 컨테이너 스타일
const TT_BOX: React.CSSProperties = {
  background: "#1e293b",  // 어두운 네이비 배경
  borderRadius: 6,
  padding: "6px 10px",
  border: "none",
};
// 툴팁 제목(날짜, 지역명 등) 스타일
const TT_LABEL: React.CSSProperties = { color: "#94a3b8", fontSize: 10, margin: "0 0 2px 0" };
// 툴팁 숫자(건수 등) 스타일
const TT_VALUE: React.CSSProperties = { color: "#fff", fontSize: 12, fontWeight: 700, margin: 0 };

// ─── 툴팁 props 타입 ─────────────────────────────────────────────────────────
//
// Recharts가 content 컴포넌트에 자동으로 넘겨주는 props 형태입니다.
// active: 마우스가 차트 위에 있는지 여부
// payload: 현재 호버된 데이터 포인트들의 배열
// label: X축 레이블 값
type TTProps = {
  active?: boolean;
  payload?: Array<{
    value?: number;
    dataKey?: string;
    payload?: Record<string, number>;
  }>;
  label?: string;
};

// ─── 도넛 차트 ───────────────────────────────────────────────────────────────

/**
 * DonutChart
 *
 * 재난 유형 또는 경보 단계별 점유율을 SVG 도넛으로 표현합니다.
 * Recharts가 아닌 순수 SVG로 직접 그립니다 (더 세밀한 제어를 위해).
 *
 * @param data  - 도넛 조각 데이터 배열. type: 라벨명, count: 건수
 * @param total - 전체 건수 합계 (중앙에 표시 + 퍼센트 계산에 사용)
 *
 * 원리:
 *   SVG circle의 stroke-dasharray를 이용해 원의 일부만 색칠합니다.
 *   c = 2πr (원의 둘레), dash = pct * c (해당 조각이 차지하는 호의 길이)
 */
export function DonutChart({
  data,
  total,
  onTypeClick,
  selectedType,
  labelFormatter,
}: {
  data: { type: string | null; count: number }[];
  total: number;
  onTypeClick?: (type: string) => void;
  selectedType?: string;
  labelFormatter?: (label: string) => string;
}) {
  const t = useI18n();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  const r = 38;              // 원 반지름 (px 기준, SVG viewBox 100x100 내)
  const c = 2 * Math.PI * r; // 원의 전체 둘레 길이
  let acc = 0;               // 누적 각도 (이전 조각들의 합)

  return (
    <div className="flex items-center gap-3 flex-1">
      {/* SVG 도넛 원 */}
      <svg viewBox="0 0 100 100" width="120" height="120" style={{ flexShrink: 0 }}>
        {/* 회색 배경 원 (전체 원) */}
        <circle cx="50" cy="50" r={r} fill="none" stroke="#f3f4f6" strokeWidth="14" />

        {data.map((d, i) => {
          const pct = d.count / total;        // 이 조각의 비율 (0~1)
          const dash = pct * c;               // 색칠할 호의 길이
          // strokeDashoffset: 시작 위치를 조정합니다 (음수 = 시계방향으로 이동)
          const offset = -((acc / total) * c);
          acc += d.count;
          return (
            <circle
              key={i}
              cx="50" cy="50" r={r}
              fill="none"
              stroke={TYPE_COLORS[i % TYPE_COLORS.length]}
              strokeWidth="14"
              // dash: 색칠할 부분 길이 / (c - dash): 투명한 나머지 길이
              strokeDasharray={`${dash} ${c - dash}`}
              strokeDashoffset={offset}
              // rotate(-90): SVG는 기본적으로 3시 방향에서 시작하므로
              //              12시 방향에서 시작하도록 -90도 회전합니다
              transform="rotate(-90 50 50)"
            />
          );
        })}

        {/* 중앙 텍스트: 총 건수 */}
        <text x="50" y="47" textAnchor="middle" fontSize="8" fill="#6b7280">{t.statsPage.total}</text>
        <text x="50" y="60" textAnchor="middle" fontSize="13" fontWeight="700" fill="#111827">
          {total.toLocaleString(locale)}
        </text>
      </svg>

      {/* 범례 목록 — onTypeClick이 있으면 클릭 가능, selectedType이 있으면 해당 항목 외 흐리게 */}
      <ul className="flex-1 space-y-1.5">
        {data.map((d, i) => {
          const key = d.type ?? t.statsPage.other;
          const label = labelFormatter ? labelFormatter(key) : key;
          const isSelected = selectedType === key;
          const isDimmed = !!selectedType && !isSelected;
          return (
            <li key={d.type ?? i}
              onClick={() => onTypeClick?.(key)}
              className={`flex items-center gap-2 text-xs rounded px-1 -mx-1 transition-opacity
                ${onTypeClick ? "cursor-pointer hover:bg-gray-50" : ""}
                ${isDimmed ? "opacity-30" : ""}`}>
              <span className="w-2 h-2 rounded-sm shrink-0"
                style={{ background: TYPE_COLORS[i % TYPE_COLORS.length] }} />
              <span className="flex-1 text-gray-700 truncate">{label}</span>
              <span className="text-gray-400">{Math.round((d.count / total) * 100)}%</span>
              <span className="font-semibold text-gray-900 min-w-[36px] text-right">
                {d.count.toLocaleString(locale)}
              </span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

// ─── 가로 막대 차트 ──────────────────────────────────────────────────────────

/**
 * HorizontalBar
 *
 * 지역별 재난 건수를 가로 막대로 표현합니다.
 * Recharts의 BarChart에 layout="vertical"을 사용하면 가로 막대가 됩니다.
 *
 * @param data - { region: 지역명, count: 건수 }[] 배열
 */
export function HorizontalBar({ data, onBarClick, labelFormatter }: { data: RegionStat[]; onBarClick?: (label: string) => void; labelFormatter?: (label: string) => string }) {
  const t = useI18n();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  if (data.length === 0) return <EmptyChart />;

  // 커스텀 툴팁: 마우스를 올렸을 때 지역명과 건수를 보여줍니다
  const TooltipContent = ({ active, payload }: TTProps) => {
    if (!active || !payload?.length) return null;
    const region = payload[0].payload?.region as unknown as string | undefined;
    return (
      <div style={TT_BOX}>
        {/* payload[0].payload는 해당 막대의 원본 데이터 객체입니다 */}
        <p style={TT_LABEL}>{region ? (labelFormatter ? labelFormatter(region) : region) : ""}</p>
        <p style={TT_VALUE}>{(payload[0].value ?? 0).toLocaleString(locale)}{t.statsPage.countUnit}</p>
      </div>
    );
  };

  return (
    <div className="flex-1 min-h-0" style={{ height: "100%" }}>
      <ResponsiveContainer width="100%" height="100%">
        {/* layout="vertical": 막대가 가로 방향으로 그려집니다 */}
        <BarChart layout="vertical" data={data} margin={{ top: 4, right: 16, bottom: 4, left: 4 }}>
          {/* type="number": 가로 축이 숫자 눈금 */}
          <XAxis type="number" hide />
          {/* type="category": 세로 축이 텍스트(지역명) */}
          {/* interval={0}: 번역된 라벨이 길어져도 Recharts가 겹침 방지를 위해 임의로 건너뛰지 않고 전부 표시 */}
          <YAxis type="category" dataKey="region" width={80} axisLine={false} tickLine={false} interval={0}
            tick={{ fontSize: 11, fill: "#374151" }} tickFormatter={labelFormatter} />
          <Tooltip content={<TooltipContent />} cursor={{ fill: "#f3f4f6" }} />
          <Bar dataKey="count" radius={[0, 3, 3, 0]} barSize={14} isAnimationActive={false}
            cursor={onBarClick ? "pointer" : "default"}
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            onClick={onBarClick ? (d: any) => onBarClick(d.region ?? "") : undefined}>
            {data.map((_, i) => (
              <Cell key={i} fill={BAR_COLORS[i % BAR_COLORS.length]} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

// ─── 세로 막대 차트 ──────────────────────────────────────────────────────────

/**
 * VerticalBar
 *
 * 유형별·지역별 건수를 세로 막대로 표현합니다.
 * 가장 많은 막대를 빨간색으로 강조합니다 (peakIdx).
 *
 * @param data - { label: 라벨명, count: 건수, color?: 개별 색상 }[] 배열
 *              color를 지정하면 해당 색상을, 없으면 CHART_COLORS 팔레트를 씁니다.
 */
export function VerticalBar({
  data,
  onBarClick,
  labelFormatter,
}: {
  data: { label: string; count: number; color?: string }[];
  onBarClick?: (label: string) => void;
  labelFormatter?: (label: string) => string;
}) {
  const t = useI18n();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  if (data.length === 0) return <EmptyChart />;

  // 가장 값이 큰 인덱스를 찾아서 빨간색으로 강조합니다
  const peakIdx = data.reduce((p, c, i) => (c.count > data[p].count ? i : p), 0);

  const TooltipContent = ({ active, payload, label }: TTProps) => {
    if (!active || !payload?.length) return null;
    return (
      <div style={TT_BOX}>
        <p style={TT_LABEL}>{label ? (labelFormatter ? labelFormatter(label) : label) : ""}</p>
        <p style={TT_VALUE}>{(payload[0].value ?? 0).toLocaleString(locale)}{t.statsPage.countUnit}</p>
      </div>
    );
  };

  return (
    <div className="flex-1 min-h-0" style={{ height: "100%" }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 4, right: 4, bottom: 4, left: 4 }}>
          <XAxis dataKey="label" axisLine={false} tickLine={false} interval={0}
            tick={{ fontSize: 10, fill: "#9ca3af" }} tickFormatter={labelFormatter} />
          <YAxis axisLine={false} tickLine={false}
            tick={{ fontSize: 10, fill: "#9ca3af" }} width={32} />
          <Tooltip content={<TooltipContent />} cursor={{ fill: "#f3f4f6" }} />
          <Bar dataKey="count" radius={[3, 3, 0, 0]} maxBarSize={40} isAnimationActive={false}
            cursor={onBarClick ? "pointer" : "default"}
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            onClick={onBarClick ? (d: any) => onBarClick(d.label ?? "") : undefined}>
            {data.map((d, i) => (
              <Cell key={i}
                fill={d.color ?? (i === peakIdx ? "#ef4444" : CHART_COLORS[i % CHART_COLORS.length])} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

// ─── 경보 단계 카드 ───────────────────────────────────────────────────────────

/**
 * LevelsCard
 *
 * 재난 경보 단계(안전안내·긴급재난·위급재난)의 건수와 비율을
 * 색상 카드 + 게이지 바 형태로 보여줍니다.
 *
 * @param data - { level: "LEVEL_1"|"LEVEL_2"|"LEVEL_3"|null, count: number }[]
 *              LEVEL_META(_constants.ts)에서 각 단계의 색상·텍스트를 가져옵니다.
 */
export function LevelsCard({ data }: { data: LevelStat[] }) {
  const t = useI18n();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  const levelMeta = getLevelMeta(t);
  // LEVEL_1, LEVEL_2, LEVEL_3 순서를 고정하고 데이터가 없으면 count=0으로 채웁니다
  const levels = ["LEVEL_1", "LEVEL_2", "LEVEL_3"].map(code => {
    const meta = levelMeta[code];
    const count = data.find(d => d.level === code)?.count ?? 0;
    return { code, ...meta, count };
  });

  const total = levels.reduce((s, l) => s + l.count, 0) || 1; // 0으로 나누기 방지
  const urgentPct = Math.round((levels[2].count / total) * 100); // LEVEL_3(위급) 비율

  return (
    <div className="flex flex-col gap-3 flex-1">
      {/* 단계별 카드 3개 */}
      <div className="flex gap-2">
        {levels.map(l => (
          <div key={l.code}
            className={`flex-1 text-center rounded-lg border py-2.5 ${l.bg} ${l.border}`}>
            <div className={`text-xs font-semibold mb-1 ${l.textCls}`}>{l.text}</div>
            <div className={`text-xl font-extrabold ${l.textCls}`}>
              {l.count.toLocaleString(locale)}
            </div>
            <div className="text-xs text-gray-400">
              {Math.round((l.count / total) * 100)}%
            </div>
          </div>
        ))}
      </div>

      {/* 비율 게이지 바: flex의 flex 값을 건수로 지정하면 비율대로 나눠집니다 */}
      <div className="flex h-2.5 rounded-full overflow-hidden">
        {levels.map(l => (
          <div key={l.code} style={{ flex: l.count || 0, background: l.solid }} />
        ))}
      </div>

      <p className="text-xs text-gray-500 leading-relaxed">
        {formatMessage(t.statsPage.urgentSummary, { total: total.toLocaleString(locale), percent: urgentPct })}
      </p>
    </div>
  );
}
