"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import { useLanguageStore } from "@/store/languageStore";
import { useFavoriteRegions } from "@/lib/queries/useFavoriteRegions";
import { useAddFavoriteRegion } from "@/lib/mutations/useAddFavoriteRegion";
import { useDeleteFavoriteRegion } from "@/lib/mutations/useDeleteFavoriteRegion";
import { useGuestFavoriteSync } from "@/hooks/useGuestFavoriteSync";
import { useSigungu } from "@/lib/queries/useAlerts";
import { useI18n } from "@/hooks/useI18n";
import { METROS } from "@/ui/metros";
import type { MemberFavoriteRegion } from "@/types/memberFavoriteRegion";

const MAX_REGIONS = 5;

// 관심지역의 legalDistrictCode 앞 2자리(시도 코드) → 시도명 폴백 매핑
// (regionName 문자열에서 시도를 못 찾을 때 사용)
const SIDO_BY_CODE_PREFIX: Record<string, string> = {
  "11": "서울특별시",
  "26": "부산광역시",
  "27": "대구광역시",
  "28": "인천광역시",
  "29": "광주광역시",
  "30": "대전광역시",
  "31": "울산광역시",
  "36": "세종특별자치시",
  "41": "경기도",
  "43": "충청북도",
  "44": "충청남도",
  "46": "전라남도",
  "47": "경상북도",
  "48": "경상남도",
  "50": "제주특별자치도",
  "51": "강원특별자치도",
  "52": "전북특별자치도",
};

export default function FavoriteRegionsPage() {
  const router = useRouter();
  const isLoggedIn = useAuthStore((s) => s.user !== null);
  const language = useLanguageStore((s) => s.language);
  const t = useI18n();

  // 로그인 시 게스트 데이터를 서버에 자동 병합
  useGuestFavoriteSync();

  const { data: regions = [], isLoading } = useFavoriteRegions();

  const [selectedSido, setSelectedSido] = useState<string>("");
  const [selectedSigunguCode, setSelectedSigunguCode] = useState<string>("");
  const [errorMsg, setErrorMsg] = useState<string>("");

  const { data: sigunguList = [], isLoading: sigunguLoading } = useSigungu(
    selectedSido || undefined,
    language
  );

  const getRegionName = (code: string) => {
    const found = sigunguList.find((s) => (s.code ?? "") === code);
    if (!found) return code;
    return found.name === "전체"
      ? `${selectedSido} 전체`
      : (found.translatedName ?? found.name);
  };

  const getDisplayRegionName = (region: MemberFavoriteRegion) => region.regionName;

  const addMutation = useAddFavoriteRegion({
    onSuccessCallback: () => {
      setSelectedSido("");
      setSelectedSigunguCode("");
      setErrorMsg("");
    },
    onErrorCallback: (msg) => setErrorMsg(msg),
    getRegionName,
  });

  const deleteMutation = useDeleteFavoriteRegion({
    onErrorCallback: (msg) => setErrorMsg(msg),
  });

  const handleAdd = () => {
    if (!selectedSigunguCode) {
      setErrorMsg("지역을 선택해주세요.");
      return;
    }
    if (regions.some((region) => region.legalDistrictCode === selectedSigunguCode)) {
      setErrorMsg("이미 등록된 지역입니다.");
      return;
    }
    if (regions.length >= MAX_REGIONS) {
      setErrorMsg(`관심지역은 최대 ${MAX_REGIONS}개까지 등록할 수 있습니다.`);
      return;
    }
    addMutation.mutate(selectedSigunguCode);
  };

  const handleDelete = (code: string) => {
    if (!confirm("이 관심지역을 삭제하시겠습니까?")) return;
    deleteMutation.mutate(code);
  };

  // 등록된 지역 클릭 시 해당 지역으로 필터링된 재난 문자 목록으로 이동
  const handleRegionClick = (region: MemberFavoriteRegion) => {
    const regionName = (region.regionName ?? "").replace(/\s+/g, " ").trim();
    const nameSido = METROS.find((sido) => regionName === sido || regionName.startsWith(`${sido} `));
    const sido = nameSido ?? SIDO_BY_CODE_PREFIX[region.legalDistrictCode?.slice(0, 2) ?? ""];
    if (!sido) return;

    const remainder = nameSido ? regionName.slice(nameSido.length).trim() : regionName;
    const query = new URLSearchParams({ sido });
    if (remainder && remainder !== "전체") query.set("sigungu", remainder);

    router.push(`/alerts?${query.toString()}#list`);
  };

  return (
    <main className="bg-[var(--canvas)] min-h-[calc(100vh-48px)]">
      <div className="mx-auto max-w-6xl px-4 py-8 space-y-5">
      <header>
        <h1 className="text-2xl font-bold tracking-tight text-[var(--ink)]">관심지역 설정</h1>
        <p className="mt-1 text-[13px] text-[var(--text-muted)]">
          최대 {MAX_REGIONS}개의 관심지역을 등록하고 재난문자 알림을 받을 수 있습니다.
        </p>
      </header>

      {/* 비로그인 안내 배너 */}
      {!isLoggedIn && (
        <div className="flex items-start gap-3 rounded-[var(--radius-card)] border border-[#cfe0fb] bg-[var(--blue-soft)] p-4 text-[13px] text-[var(--text-body)]">
          <svg className="mt-0.5 shrink-0 text-[var(--blue)]" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="16" x2="12" y2="12" />
            <line x1="12" y1="8" x2="12.01" y2="8" />
          </svg>
          <div>
            <p className="font-semibold text-[var(--ink)]">비로그인 상태입니다.</p>
            <p className="mt-0.5 text-[var(--text-muted)]">
              관심지역은 이 기기의 브라우저에 임시 저장됩니다.{" "}
              <Link href="/login" className="font-semibold text-[var(--blue)] hover:underline">
                로그인
              </Link>
              하시면 서버에 영구 저장되며 다른 기기에서도 사용할 수 있습니다.
            </p>
          </div>
        </div>
      )}

      {errorMsg && (
        <div role="alert" aria-live="assertive" className="rounded-[var(--radius-card)] border border-[#f3c7c1] bg-[var(--coral-soft)] p-3 text-[13px] text-[#b4453a]">
          {errorMsg}
        </div>
      )}

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
        {/* 왼쪽: 등록된 관심지역 */}
        <section className="rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-6 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-[var(--ink)]">등록된 관심지역</h2>
            <span className="text-[13px] text-[var(--text-muted)]">
              {regions.length} / {MAX_REGIONS}
            </span>
          </div>

          {isLoading ? (
            <div className="text-[13px] text-[var(--text-muted)]">{t.loading}</div>
          ) : regions.length === 0 ? (
            <div className="py-8 text-center text-[13px] text-[var(--text-muted)]">
              아직 등록된 관심지역이 없습니다.
            </div>
          ) : (
            <ul className="space-y-2">
              {regions.map((r) => (
                <li
                  key={r.legalDistrictCode}
                  className="flex items-center gap-3 rounded-[var(--radius-card)] border border-[var(--line)] transition-colors hover:border-[#cfe0fb] hover:bg-[var(--blue-soft)]"
                >
                  <button
                    type="button"
                    className="min-w-0 flex-1 cursor-pointer rounded-l-[var(--radius-card)] px-3 py-3 text-left font-medium text-[var(--text-body)] focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--blue)]"
                    aria-label={`${getDisplayRegionName(r)}: ${t.alertList.title}`}
                    onClick={() => handleRegionClick(r)}
                  >
                    <span className="block truncate">{getDisplayRegionName(r)}</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDelete(r.legalDistrictCode)}
                    disabled={deleteMutation.isPending}
                    className="mr-3 shrink-0 rounded-[var(--radius-control)] border border-[var(--coral)] bg-[var(--coral)] px-3 py-1 text-sm font-semibold text-white transition hover:brightness-95 disabled:opacity-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--coral-soft)] focus-visible:ring-offset-1"
                  >
                    삭제
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* 오른쪽: 지역 추가 */}
        <section className="space-y-4 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-6 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
          <h2 className="text-lg font-semibold text-[var(--ink)]">지역 추가</h2>

          <div className="space-y-3">
            <div>
              <label className="mb-1 block text-[13px] text-[var(--text-muted)]">시/도</label>
              <select
                value={selectedSido}
                onChange={(e) => {
                  setSelectedSido(e.target.value);
                  setSelectedSigunguCode("");
                }}
                className="input"
              >
                <option value="">시/도를 선택하세요</option>
                {METROS.map((m) => (
                  <option key={m} value={m}>
                    {t.metros[m]}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="mb-1 block text-[13px] text-[var(--text-muted)]">시/군/구</label>
              <select
                value={selectedSigunguCode}
                onChange={(e) => setSelectedSigunguCode(e.target.value)}
                className="input disabled:opacity-60"
                disabled={!selectedSido || sigunguLoading}
              >
                <option value="">
                  {!selectedSido
                    ? "먼저 시/도를 선택하세요"
                    : sigunguLoading
                      ? t.loading
                      : "시/군/구 또는 전체를 선택하세요"}
                </option>
                {sigunguList.map((s) => (
                  <option key={s.code ?? s.name} value={s.code ?? ""}>
                    {s.name === "전체"
                      ? `${t.metros[selectedSido as keyof typeof t.metros] ?? selectedSido} 전체`
                      : s.translatedName ?? s.name}
                  </option>
                ))}
              </select>
            </div>

            <button
              onClick={handleAdd}
              disabled={
                !selectedSigunguCode ||
                addMutation.isPending ||
                regions.length >= MAX_REGIONS
              }
              className="h-11 w-full rounded-[var(--radius-control)] bg-[var(--blue)] text-sm font-semibold text-white transition hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--blue)]"
            >
              {addMutation.isPending ? "추가 중..." : "+ 관심지역 추가"}
            </button>
          </div>

          <p className="rounded-[var(--radius-card)] bg-[var(--blue-soft)] p-3 text-[13px] text-[var(--blue)]">
            시/도 전체 또는 시/군/구 단위로 등록할 수 있습니다. 시/군/구 목록에서 &quot;{selectedSido || "시/도"} 전체&quot;를 선택하면 해당 시/도 전체 알림을 받게 됩니다.
          </p>
        </section>
      </div>
      </div>
    </main>
  );
}
