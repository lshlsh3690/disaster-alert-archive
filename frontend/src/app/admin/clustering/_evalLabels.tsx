"use client";

import { Fragment, useEffect, useState } from "react";
import { isAxiosError } from "axios";
import type { EvalLabelPair, EvalLabelRelation, HumanLabel } from "@/api/clusteringAdminApi";
import { useUpdateEvalLabel } from "@/lib/mutations/useUpdateEvalLabel";
import { useEvalLabelDatasets, useEvalLabelHistory, useEvalLabels } from "@/lib/queries/useClusteringAdmin";
import { formatDateTime, formatNumber } from "./_format";
import { AlertCell, HUMAN_LABEL_OPTIONS, QueryState, Section } from "./_sections";

// 평가 셋 라벨 — 클러스터링 평가의 정답(사람 라벨)을 셋별로 보고 고친다. 고칠 때는 사유를 받아 이력에 남긴다.
// 평가 도구(--eval.dataset)가 이 값을 읽으므로, 여기서 고친 라벨이 다음 평가의 정답이 된다.

const SELECT = "border border-[var(--line)] rounded-[var(--radius-control)] px-2 py-1 bg-[var(--surface)] text-[var(--text-body)]";
const PAGE_SIZE = 20;

// 이 표는 좌우 스크롤 없이 화면 너비에 다 담는다 — 칸 비율을 고정(table-fixed)하고, 모든 칸이 칸 안에서 줄바꿈한다.
// 검증 화면의 표(TABLE·TH·TD)는 줄바꿈을 막아 두어서 여기서는 따로 둔다
const TABLE = "w-full table-fixed text-sm border-collapse";
const TH = "px-2 py-2 text-left font-medium text-[var(--text-muted)] border-b border-[var(--line)] align-bottom";
const TD = "px-2 py-2 border-b border-[var(--line)] align-top [overflow-wrap:anywhere]";
// 칸 비율(합 100%). 원문이 긴 두 알림 칸에 가장 많이 준다
const COLUMNS = ["4%", "7%", "7%", "26%", "26%", "13%", "11%", "6%"];

const RELATION_LABEL: Record<EvalLabelRelation, string> = {
  SAME_SIGUNGU: "같은 시군구",
  ADJACENT_SIGUNGU: "이웃 시군구",
};

const LABEL_TEXT: Record<HumanLabel, string> = Object.fromEntries(
  HUMAN_LABEL_OPTIONS.map((o) => [o.value, o.label]),
) as Record<HumanLabel, string>;

/**
 * 두 알림의 발송 간격. 같은 사건인지 판단할 때 가장 먼저 보는 단서라 따로 보인다.
 * 24시간 미만은 "N시간 M분", 이상은 "N일 M시간". 한쪽 시각이 없으면 "-".
 */
export function formatGap(a: string | null, b: string | null): string {
  if (!a || !b) return "-";
  const minutes = Math.round(Math.abs(new Date(b).getTime() - new Date(a).getTime()) / 60000);
  if (Number.isNaN(minutes)) return "-";
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  if (days > 0) return `${days}일 ${hours}시간`;
  return `${hours}시간 ${minutes % 60}분`;
}

function errorMessage(e: unknown, fallback: string): string {
  const message = isAxiosError(e) ? (e.response?.data as { message?: string } | undefined)?.message : undefined;
  return message ?? fallback;
}

/** 평가 셋 라벨 영역. 셋을 고르면 그 셋의 라벨 쌍을 넣은 순서대로 보여 주고, 쌍마다 라벨을 사유와 함께 고친다. */
export function EvalLabelsSection() {
  const datasets = useEvalLabelDatasets();
  const [picked, setPicked] = useState<string | null>(null);
  const [label, setLabel] = useState<HumanLabel | null>(null);
  const [page, setPage] = useState(0);
  // 고른 셋이 없으면 첫 셋을 쓴다 — 셋이 하나뿐인 지금은 고르지 않아도 바로 보인다
  const dataset = picked ?? datasets.data?.datasets[0]?.name ?? null;
  const summary = datasets.data?.datasets.find((d) => d.name === dataset);
  const list = useEvalLabels(dataset ? { dataset, label, page, size: PAGE_SIZE } : null);

  // 필터 때문에 마지막 페이지가 비면(예: 모르겠음만 보다가 마지막 쌍을 고침) 있는 마지막 페이지로 당긴다
  useEffect(() => {
    if (list.data && !list.isPlaceholderData && list.data.totalPages > 0 && page >= list.data.totalPages) {
      setPage(list.data.totalPages - 1);
    }
  }, [list.data, list.isPlaceholderData, page]);

  const changeDataset = (value: string) => {
    setPicked(value);
    setPage(0);
  };
  const changeLabel = (value: string) => {
    setLabel(value === "" ? null : (value as HumanLabel));
    setPage(0);
  };

  return (
    <Section
      title="평가 셋 라벨"
      description="클러스터링 평가의 정답(사람 라벨)입니다. 라벨을 바꾸려면 사유를 적어야 하고, 바꿀 때마다 이력이 남습니다. 평가 도구(--eval.dataset)가 이 값을 읽습니다."
    >
      <QueryState isLoading={datasets.isLoading} error={datasets.error} />
      {datasets.data && datasets.data.datasets.length === 0 && (
        <p className="text-sm text-[var(--text-muted)]">
          평가 셋 라벨이 없습니다. backend/evaluation/datasets/pair_label_data_*.sql 을 이 DB 에 넣어 주세요.
        </p>
      )}
      {datasets.data && dataset && (
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <label className="text-[var(--text-muted)]" htmlFor="eval-dataset">평가 셋</label>
          <select id="eval-dataset" value={dataset} onChange={(e) => changeDataset(e.target.value)} className={SELECT}>
            {datasets.data.datasets.map((d) => (
              <option key={d.name} value={d.name}>
                {d.name} ({formatNumber(d.pairs)}쌍)
              </option>
            ))}
          </select>
          <label className="text-[var(--text-muted)]" htmlFor="eval-label-filter">라벨</label>
          <select id="eval-label-filter" value={label ?? ""} onChange={(e) => changeLabel(e.target.value)} className={SELECT}>
            <option value="">전체</option>
            {HUMAN_LABEL_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
          {summary && (
            <span className="text-[var(--text-muted)]">
              같은 사건 {formatNumber(summary.same)} · 다른 사건 {formatNumber(summary.diff)} · 모르겠음 {formatNumber(summary.unsure)}
              {" · "}마지막 수정 {summary.lastUpdatedAt ? formatDateTime(summary.lastUpdatedAt) : "없음"}
            </span>
          )}
        </div>
      )}
      {dataset && <QueryState isLoading={list.isLoading} error={list.error} />}
      {list.data && list.data.pairs.length === 0 && (
        <p className="text-sm text-[var(--text-muted)]">이 조건에 맞는 라벨 쌍이 없습니다.</p>
      )}
      {list.data && list.data.pairs.length > 0 && (
        <EvalLabelTable pairs={list.data.pairs} startIndex={list.data.page * list.data.size} />
      )}
      {list.data && list.data.totalPages > 1 && (
        <div className="flex items-center gap-2 text-sm">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="px-3 py-1 border border-[var(--line)] rounded-[var(--radius-control)] disabled:opacity-40"
          >
            이전
          </button>
          <span className="text-[var(--text-muted)]">
            {page + 1} / {list.data.totalPages} (총 {formatNumber(list.data.totalElements)}쌍)
          </span>
          <button
            onClick={() => setPage((p) => p + 1)}
            // 이전 응답(placeholder)의 totalPages 로 넘어가면 새 조건에 없는 빈 페이지를 요청하게 된다
            disabled={list.isPlaceholderData || page + 1 >= list.data.totalPages}
            className="px-3 py-1 border border-[var(--line)] rounded-[var(--radius-control)] disabled:opacity-40"
          >
            다음
          </button>
        </div>
      )}
    </Section>
  );
}

/**
 * 라벨 쌍 표. 수정은 한 번에 하나만 보낸다(저장 중에는 모든 줄의 버튼을 막는다).
 * 순번은 지금 조건(셋·라벨 필터)의 목록에서 몇 번째인지다(1부터, 페이지를 넘겨도 이어진다).
 */
function EvalLabelTable({ pairs, startIndex }: { pairs: EvalLabelPair[]; startIndex: number }) {
  const update = useUpdateEvalLabel();
  const [openHistory, setOpenHistory] = useState<number | null>(null);

  return (
    <div>
      <table className={TABLE}>
        <colgroup>
          {COLUMNS.map((w, i) => <col key={i} style={{ width: w }} />)}
        </colgroup>
        <thead>
          <tr>
            <th className={TH}>순번</th>
            <th className={TH}>관계</th>
            <th className={TH}>간격</th>
            <th className={TH}>알림 A</th>
            <th className={TH}>알림 B</th>
            <th className={TH}>라벨</th>
            <th className={TH}>기준 시점 · 마지막 수정</th>
            <th className={TH}>이력</th>
          </tr>
        </thead>
        <tbody>
          {pairs.map((p, i) => (
            <Fragment key={p.id}>
              <tr>
                <td className={TD}>{startIndex + i + 1}</td>
                <td className={TD}>{p.relation ? RELATION_LABEL[p.relation] : "-"}</td>
                <td className={TD}>{formatGap(p.alertA.sentAt, p.alertB.sentAt)}</td>
                <td className={TD}><AlertCell alert={p.alertA} fluid /></td>
                <td className={TD}><AlertCell alert={p.alertB} fluid /></td>
                <td className={TD}>
                  <LabelEditor pair={p} saving={update.isPending} onSave={(label, reason, callbacks) =>
                    update.mutate({ id: p.id, body: { label, reason } }, callbacks)} />
                </td>
                <td className={TD}>
                  <div>{p.criteriaDate ?? "-"}</div>
                  <div className="text-xs text-[var(--text-muted)]">{p.updatedAt ? formatDateTime(p.updatedAt) : "고친 적 없음"}</div>
                </td>
                <td className={TD}>
                  <button
                    type="button"
                    aria-expanded={openHistory === p.id}
                    onClick={() => setOpenHistory((id) => (id === p.id ? null : p.id))}
                    className="px-2 py-0.5 text-xs border border-[var(--line)] rounded-[var(--radius-control)] hover:bg-[var(--blue-soft)]"
                  >
                    {openHistory === p.id ? "닫기" : "보기"}
                  </button>
                </td>
              </tr>
              {openHistory === p.id && (
                <tr>
                  <td className={TD} colSpan={8}>
                    <EvalLabelHistory id={p.id} />
                  </td>
                </tr>
              )}
            </Fragment>
          ))}
        </tbody>
      </table>
    </div>
  );
}

type SaveCallbacks = { onSuccess: () => void; onError: (e: unknown) => void };

/**
 * 쌍 하나의 라벨 편집. 지금 라벨과 다른 버튼을 누르면 사유 입력 칸이 열리고, 사유를 적고 저장해야 바뀐다.
 * 같은 라벨을 누르면 아무것도 하지 않는다(서버도 같은 라벨은 바꾸지 않는다).
 */
function LabelEditor({ pair, saving, onSave }: {
  pair: EvalLabelPair;
  saving: boolean;
  onSave: (label: HumanLabel, reason: string, callbacks: SaveCallbacks) => void;
}) {
  const [draft, setDraft] = useState<HumanLabel | null>(null);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  const choose = (value: HumanLabel) => {
    setError(null);
    if (value === pair.label) {
      setDraft(null);
      return;
    }
    setDraft(value);
  };
  const cancel = () => {
    setDraft(null);
    setReason("");
    setError(null);
  };
  const save = () => {
    if (!draft || reason.trim() === "") return;
    onSave(draft, reason.trim(), {
      onSuccess: cancel,
      onError: (e) => setError(errorMessage(e, "저장하지 못했습니다.")),
    });
  };

  return (
    <div className="space-y-1">
      <div className="flex flex-wrap gap-1" role="group" aria-label={`라벨 쌍 #${pair.id} 라벨`}>
        {HUMAN_LABEL_OPTIONS.map((o) => {
          const current = pair.label === o.value;
          const chosen = draft === o.value;
          return (
            <button
              key={o.value}
              type="button"
              aria-pressed={current}
              disabled={saving}
              onClick={() => choose(o.value)}
              className={`px-2 py-0.5 text-xs rounded-[var(--radius-control)] border disabled:opacity-40 ${
                current
                  ? "border-[var(--blue)] bg-[var(--blue)] text-white"
                  : chosen
                    ? "border-[var(--blue)] text-[var(--blue)] bg-[var(--blue-soft)]"
                    : "border-[var(--line)] text-[var(--text-body)] hover:bg-[var(--blue-soft)]"
              }`}
            >
              {o.label}
            </button>
          );
        })}
      </div>
      {draft && (
        <div className="space-y-1">
          <p className="text-xs text-[var(--text-muted)]">
            {LABEL_TEXT[pair.label]} → {LABEL_TEXT[draft]} 로 바꿉니다. 사유를 적어 주세요.
          </p>
          <textarea
            aria-label="바꾸는 사유"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={2}
            className="w-full border border-[var(--line)] rounded-[var(--radius-control)] px-2 py-1 text-xs bg-[var(--surface)] text-[var(--text-body)]"
          />
          <div className="flex gap-1">
            <button
              type="button"
              onClick={save}
              disabled={saving || reason.trim() === ""}
              className="px-2 py-0.5 text-xs rounded-[var(--radius-control)] border border-[var(--blue)] bg-[var(--blue)] text-white disabled:opacity-40"
            >
              {saving ? "저장 중…" : "저장"}
            </button>
            <button
              type="button"
              onClick={cancel}
              disabled={saving}
              className="px-2 py-0.5 text-xs rounded-[var(--radius-control)] border border-[var(--line)] text-[var(--text-body)] disabled:opacity-40"
            >
              취소
            </button>
          </div>
        </div>
      )}
      {error && <p className="text-xs text-[var(--coral)]">{error}</p>}
    </div>
  );
}

/** 라벨 하나의 변경 이력(최신 먼저). 펼친 줄에서만 그려져 펼치지 않은 줄은 요청하지 않는다. */
function EvalLabelHistory({ id }: { id: number }) {
  const { data, isLoading, error } = useEvalLabelHistory(id);
  if (isLoading || error) return <QueryState isLoading={isLoading} error={error} />;
  if (!data || data.changes.length === 0) {
    return <p className="text-sm text-[var(--text-muted)]">바뀐 적 없는 라벨입니다.</p>;
  }
  return (
    <ul className="space-y-1 text-sm whitespace-normal">
      {data.changes.map((c, i) => (
        <li key={`${c.changedAt}-${i}`} className="text-[var(--text-body)]">
          <span className="text-[var(--text-muted)]">{formatDateTime(c.changedAt)} · 회원 #{c.changedBy}</span>
          {" · "}
          {LABEL_TEXT[c.oldLabel]} → {LABEL_TEXT[c.newLabel]}
          {" · "}
          {c.reason}
        </li>
      ))}
    </ul>
  );
}
