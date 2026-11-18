"use client";

import { useState } from "react";
import type { ClusteringAlertSummary, DayStatus, DisagreementKind } from "@/api/clusteringAdminApi";
import {
  useClusteringDailyChecks,
  useClusteringDailyScores,
  useClusteringDisagreements,
  useClusteringJudgeVersions,
} from "@/lib/queries/useClusteringAdmin";
import { formatDateTime, formatNumber, formatRatio } from "./_format";

// 관리자 클러스터링 화면의 네 영역. 모두 읽기 전용이고 이미 기록된 값만 보여 준다(새 LLM 호출 없음).

const TABLE = "w-full text-sm border-collapse";
const TH = "px-3 py-2 text-left font-medium text-[var(--text-muted)] border-b border-[var(--line)] whitespace-nowrap";
const TD = "px-3 py-2 border-b border-[var(--line)] align-top whitespace-nowrap";

const STATUS_LABEL: Record<DayStatus, string> = {
  SCORED: "채점됨",
  NO_PAIRS: "판정 0쌍",
  NO_RECORD: "기록 없음",
};

const KIND_LABEL: Record<DisagreementKind, string> = {
  OVER_MERGED: "과병합",
  MISSED: "과소병합",
};

export function Section({ title, description, children }: { title: string; description?: string; children: React.ReactNode }) {
  return (
    <section className="bg-[var(--surface)] border border-[var(--line)] rounded-[var(--radius-compact)] p-4 space-y-3">
      <div>
        <h2 className="text-base font-semibold text-[var(--ink)]">{title}</h2>
        {description && <p className="text-xs text-[var(--text-muted)] mt-1">{description}</p>}
      </div>
      {children}
    </section>
  );
}

function QueryState({ isLoading, isError }: { isLoading: boolean; isError: boolean }) {
  if (isLoading) return <p className="text-sm text-[var(--text-muted)]">불러오는 중…</p>;
  if (isError) return <p className="text-sm text-[var(--coral)]">불러오지 못했습니다.</p>;
  return null;
}

/** 일일 채점 — 날짜마다 점수와 같은 줄에 그날 판사 버전·일치율을 둔다(점수만 따로 보이지 않게). */
export function DailyScoresSection({ from, to }: { from: string; to: string }) {
  const { data, isLoading, isError } = useClusteringDailyScores(from, to);
  return (
    <Section title="일일 채점" description="추정 비용은 프롬프트 캐시 할인을 빼지 않은 상한값입니다.">
      <QueryState isLoading={isLoading} isError={isError} />
      {data && (
        <div className="overflow-x-auto">
          <table className={TABLE}>
            <thead>
              <tr>
                <th className={TH}>날짜</th>
                <th className={TH}>상태</th>
                <th className={TH}>사건 알림</th>
                <th className={TH}>판정 쌍</th>
                <th className={TH}>맞게 묶은 비율</th>
                <th className={TH}>빠짐없이 묶은 비율</th>
                <th className={TH}>점수</th>
                <th className={TH}>과병합 / 과소병합</th>
                <th className={TH}>UNSURE / 형식 실패</th>
                <th className={TH}>판사</th>
                <th className={TH}>판사 일치율 (같은 / 다른)</th>
                <th className={TH}>토큰 (입력 / 출력)</th>
                <th className={TH}>추정 비용 (상한)</th>
              </tr>
            </thead>
            <tbody>
              {data.days.map((d) => (
                <tr key={d.date} className={d.status === "NO_RECORD" ? "text-[var(--text-muted)]" : ""}>
                  <td className={TD}>{d.date}</td>
                  <td className={TD}>{STATUS_LABEL[d.status]}</td>
                  {d.status === "NO_RECORD" ? (
                    <td className={TD} colSpan={11}>-</td>
                  ) : (
                    <>
                      <td className={TD}>{formatNumber(d.incidentCount)}</td>
                      <td className={TD}>{formatNumber(d.pairCount)}</td>
                      <td className={TD}>{formatRatio(d.purity)}</td>
                      <td className={TD}>{formatRatio(d.completeness)}</td>
                      <td className={TD}>{d.score === undefined ? "계산할 수 없음" : d.score.toFixed(1)}</td>
                      <td className={TD}>{formatNumber(d.overMerged)} / {formatNumber(d.missed)}</td>
                      <td className={TD}>{formatNumber(d.unsureCount)} / {formatNumber(d.formatFailureCount)}</td>
                      <td className={TD}>{d.judge ? `${d.judge.version}${d.judge.model ? ` · ${d.judge.model}` : ""}` : "-"}</td>
                      <td className={TD}>
                        {d.judge ? `${formatRatio(d.judge.sameAgreement)} / ${formatRatio(d.judge.diffAgreement)}` : "-"}
                      </td>
                      <td className={TD}>{formatNumber(d.promptTokens)} / {formatNumber(d.completionTokens)}</td>
                      <td className={TD}>{d.estimatedCostUsd === undefined ? "알 수 없음" : `$${d.estimatedCostUsd.toFixed(3)}`}</td>
                    </>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Section>
  );
}

function AlertCell({ alert }: { alert: ClusteringAlertSummary }) {
  if (alert.message === null) {
    return <span className="text-[var(--text-muted)]">#{alert.id} · 원문 없음</span>;
  }
  return (
    <div className="space-y-1 whitespace-normal min-w-[16rem] max-w-[28rem]">
      <div className="text-xs text-[var(--text-muted)]">
        #{alert.id} · {formatDateTime(alert.sentAt)} · {alert.type ?? "-"}
      </div>
      <div className="text-[var(--text-body)]">{alert.message}</div>
    </div>
  );
}

/** 불일치 쌍 — 판사와 시스템이 다르게 본 쌍을 두 알림 원문과 함께 보여 준다. */
export function DisagreementsSection({ from, to }: { from: string; to: string }) {
  const [kind, setKind] = useState<DisagreementKind | null>(null);
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useClusteringDisagreements({ from, to, kind, page, size: 20 });

  const changeKind = (value: string) => {
    setKind(value === "" ? null : (value as DisagreementKind));
    setPage(0);
  };

  return (
    <Section title="불일치 쌍" description="과병합 = 시스템은 묶었는데 판사는 다른 사건, 과소병합 = 판사는 같은 사건인데 시스템이 안 묶음.">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <label className="text-[var(--text-muted)]" htmlFor="disagreement-kind">종류</label>
        <select
          id="disagreement-kind"
          value={kind ?? ""}
          onChange={(e) => changeKind(e.target.value)}
          className="border border-[var(--line)] rounded-[var(--radius-control)] px-2 py-1 bg-[var(--surface)] text-[var(--text-body)]"
        >
          <option value="">전체</option>
          <option value="OVER_MERGED">과병합</option>
          <option value="MISSED">과소병합</option>
        </select>
        {data && <span className="text-[var(--text-muted)]">총 {formatNumber(data.totalElements)}쌍</span>}
      </div>
      <QueryState isLoading={isLoading} isError={isError} />
      {data && data.pairs.length === 0 && <p className="text-sm text-[var(--text-muted)]">이 범위에 불일치 쌍이 없습니다.</p>}
      {data && data.pairs.length > 0 && (
        <div className="overflow-x-auto">
          <table className={TABLE}>
            <thead>
              <tr>
                <th className={TH}>날짜</th>
                <th className={TH}>종류</th>
                <th className={TH}>판사</th>
                <th className={TH}>알림 A</th>
                <th className={TH}>알림 B</th>
                <th className={TH}>당시 이벤트 (A / B)</th>
              </tr>
            </thead>
            <tbody>
              {data.pairs.map((p) => (
                <tr key={`${p.date}-${p.alertA.id}-${p.alertB.id}`}>
                  <td className={TD}>{p.date}</td>
                  <td className={TD}>{KIND_LABEL[p.kind]}</td>
                  <td className={TD}>{p.judgeVerdict}</td>
                  <td className={TD}><AlertCell alert={p.alertA} /></td>
                  <td className={TD}><AlertCell alert={p.alertB} /></td>
                  <td className={TD}>{p.eventA ?? "미배정"} / {p.eventB ?? "미배정"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && data.totalPages > 1 && (
        <div className="flex items-center gap-2 text-sm">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="px-3 py-1 border border-[var(--line)] rounded-[var(--radius-control)] disabled:opacity-40"
          >
            이전
          </button>
          <span className="text-[var(--text-muted)]">{page + 1} / {data.totalPages}</span>
          <button
            onClick={() => setPage((p) => p + 1)}
            disabled={page + 1 >= data.totalPages}
            className="px-3 py-1 border border-[var(--line)] rounded-[var(--radius-control)] disabled:opacity-40"
          >
            다음
          </button>
        </div>
      )}
    </Section>
  );
}

/** 구조 검사(매일 00:30) — 미배정·임베딩 없음이 있는 날을 강조한다. */
export function DailyChecksSection({ from, to }: { from: string; to: string }) {
  const { data, isLoading, isError } = useClusteringDailyChecks(from, to);
  return (
    <Section title="구조 검사 (매일 00:30)" description="미배정이나 임베딩 없음이 있는 날은 강조됩니다. 검사가 돌지 않은 날은 '기록 없음'입니다.">
      <QueryState isLoading={isLoading} isError={isError} />
      {data && (
        <div className="overflow-x-auto">
          <table className={TABLE}>
            <thead>
              <tr>
                <th className={TH}>날짜</th>
                <th className={TH}>사건 알림</th>
                <th className={TH}>미배정</th>
                <th className={TH}>임베딩 없음</th>
              </tr>
            </thead>
            <tbody>
              {data.days.map((d) => (
                <tr
                  key={d.date}
                  className={!d.recorded ? "text-[var(--text-muted)]" : d.attention ? "bg-[var(--coral-soft)] font-medium" : ""}
                >
                  <td className={TD}>{d.date}</td>
                  {d.recorded ? (
                    <>
                      <td className={TD}>{formatNumber(d.incidentCount)}</td>
                      <td className={TD}>{formatNumber(d.unassignedCount)}</td>
                      <td className={TD}>{formatNumber(d.noEmbeddingCount)}</td>
                    </>
                  ) : (
                    <td className={TD} colSpan={3}>기록 없음</td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Section>
  );
}

/** 판사 버전 이력 — 버전마다 모델·272쌍 일치율과 그 버전으로 채점한 기간. */
export function JudgeVersionsSection() {
  const { data, isLoading, isError } = useClusteringJudgeVersions();
  return (
    <Section title="판사 버전 이력" description="일치율은 사람 라벨 272쌍과 판사 판정을 견준 값입니다.">
      <QueryState isLoading={isLoading} isError={isError} />
      {data && (
        <div className="overflow-x-auto">
          <table className={TABLE}>
            <thead>
              <tr>
                <th className={TH}>버전</th>
                <th className={TH}>모델</th>
                <th className={TH}>측정일</th>
                <th className={TH}>같은 사건 일치율</th>
                <th className={TH}>다른 사건 일치율</th>
                <th className={TH}>채점 기간</th>
              </tr>
            </thead>
            <tbody>
              {data.versions.map((v) => (
                <tr key={v.version}>
                  <td className={TD}>{v.version}</td>
                  <td className={TD}>{v.model ?? "-"}</td>
                  <td className={TD}>{v.measuredOn ?? "-"}</td>
                  <td className={TD}>{formatRatio(v.sameAgreement)}</td>
                  <td className={TD}>{formatRatio(v.diffAgreement)}</td>
                  <td className={TD}>
                    {v.firstScoredDate
                      ? `${v.firstScoredDate} ~ ${v.lastScoredDate} (${v.scoredDays}일)`
                      : <span className="text-[var(--text-muted)]">채점 기록 없음</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Section>
  );
}
