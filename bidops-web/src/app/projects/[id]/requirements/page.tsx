"use client";

import { useParams, useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useEffect, useState, useCallback, useRef } from "react";
import { requirementApi, analysisJobApi, documentApi } from "@/lib/api";
import StatusBadge from "@/components/common/StatusBadge";
import DataTable, { type Column } from "@/components/common/DataTable";
import FilterBar from "@/components/common/FilterBar";
import LoadingState from "@/components/common/LoadingState";
import EmptyState from "@/components/common/EmptyState";

const REVIEW_FILTERS = [
  { label: "전체", value: "" }, { label: "미검토", value: "NOT_REVIEWED" },
  { label: "검토중", value: "IN_REVIEW" }, { label: "승인", value: "APPROVED" },
  { label: "보류", value: "HOLD" }, { label: "수정필요", value: "NEEDS_UPDATE" },
];

const CATEGORY_LABELS: Record<string, string> = {
  BUSINESS_OVERVIEW: "사업개요", BACKGROUND: "배경", OBJECTIVE: "목표", SCOPE: "범위",
  FUNCTIONAL: "기능", NON_FUNCTIONAL: "비기능", PERFORMANCE: "성능", SECURITY: "보안",
  QUALITY: "품질", TESTING: "시험", DATA_INTEGRATION: "데이터/연계", UI_UX: "UI/UX",
  INFRASTRUCTURE: "인프라", PERSONNEL: "인력", TRACK_RECORD: "실적", SCHEDULE: "일정",
  DELIVERABLE: "산출물", SUBMISSION: "제출", PROPOSAL_GUIDE: "제안안내", EVALUATION: "평가",
  PRESENTATION: "발표", MAINTENANCE: "유지보수", TRAINING: "교육", LEGAL: "법률", ETC: "기타",
};

const CATEGORY_OPTIONS = [{ value: "", label: "카테고리 전체" },
  ...Object.entries(CATEGORY_LABELS).map(([k, v]) => ({ value: k, label: v }))];
const FACT_OPTIONS = [
  { value: "", label: "근거수준 전체" }, { value: "FACT", label: "확정" },
  { value: "INFERENCE", label: "추론" }, { value: "REVIEW_NEEDED", label: "검토필요" },
];
const MANDATORY_OPTIONS = [
  { value: "", label: "필수여부 전체" }, { value: "true", label: "필수" }, { value: "false", label: "선택" },
];
const QUERY_OPTIONS = [
  { value: "", label: "질의필요 전체" }, { value: "true", label: "질의필요" }, { value: "false", label: "불필요" },
];

export default function RequirementsPage() {
  const { id } = useParams() as { id: string };
  const router = useRouter();
  const searchParams = useSearchParams();
  const [items, setItems] = useState<any[]>([]);
  const [allItems, setAllItems] = useState<any[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [loading, setLoading] = useState(true);

  const [reviewFilter, setReviewFilter] = useState(searchParams.get("review_status") || "");
  const [categoryFilter, setCategoryFilter] = useState(searchParams.get("category") || "");
  const [factLevelFilter, setFactLevelFilter] = useState(searchParams.get("fact_level") || "");
  const [mandatoryFilter, setMandatoryFilter] = useState(searchParams.get("mandatory") || "");
  const [queryNeededFilter, setQueryNeededFilter] = useState(searchParams.get("query_needed") || "");
  const [qualityIssueCode, setQualityIssueCode] = useState(searchParams.get("quality_issue_code") || "");
  const [qualitySeverity, setQualitySeverity] = useState(searchParams.get("quality_severity") || "");
  const [reanalyzeFilter, setReanalyzeFilter] = useState(searchParams.get("reanalyze_status") || "");
  const [reanalyzeSort, setReanalyzeSort] = useState(searchParams.get("reanalyze_sort") || "");
  const [keyword, setKeyword] = useState(searchParams.get("keyword") || "");
  const [searchInput, setSearchInput] = useState(searchParams.get("keyword") || "");
  const [page, setPage] = useState(Number(searchParams.get("page")) || 1);
  const keywordTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // ── searchInput → keyword debounce (400ms) ──────────────────────
  useEffect(() => {
    // searchInput이 keyword와 같으면 무시 (초기화/Enter 직후)
    if (searchInput === keyword) return;
    if (keywordTimerRef.current) clearTimeout(keywordTimerRef.current);
    keywordTimerRef.current = setTimeout(() => {
      setKeyword(searchInput);
      setPage(1);
    }, 400);
    return () => { if (keywordTimerRef.current) clearTimeout(keywordTimerRef.current); };
  }, [searchInput]);

  // keyword 즉시 적용 (Enter/초기화용)
  const applyKeywordNow = useCallback(() => {
    if (keywordTimerRef.current) clearTimeout(keywordTimerRef.current);
    setKeyword(searchInput);
    setPage(1);
  }, [searchInput]);

  // ── URL 동기화: 필터 상태 → URL query ──────────────────────────
  useEffect(() => {
    const p = new URLSearchParams();
    if (reviewFilter) p.set("review_status", reviewFilter);
    if (categoryFilter) p.set("category", categoryFilter);
    if (factLevelFilter) p.set("fact_level", factLevelFilter);
    if (mandatoryFilter) p.set("mandatory", mandatoryFilter);
    if (queryNeededFilter) p.set("query_needed", queryNeededFilter);
    if (qualityIssueCode) p.set("quality_issue_code", qualityIssueCode);
    if (qualitySeverity) p.set("quality_severity", qualitySeverity);
    if (reanalyzeFilter) p.set("reanalyze_status", reanalyzeFilter);
    if (reanalyzeSort) p.set("reanalyze_sort", reanalyzeSort);
    if (keyword) p.set("keyword", keyword);
    if (page > 1) p.set("page", String(page));

    const qs = p.toString();
    const current = searchParams.toString();
    if (qs !== current) {
      router.replace(`/projects/${id}/requirements${qs ? `?${qs}` : ""}`, { scroll: false });
    }
  }, [reviewFilter, categoryFilter, factLevelFilter, mandatoryFilter, queryNeededFilter,
      qualityIssueCode, qualitySeverity, reanalyzeFilter, reanalyzeSort, keyword, page, id]);

  // 일괄 선택 + 재분석
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [batchState, setBatchState] = useState<"idle" | "requesting" | "done">("idle");
  const [batchResult, setBatchResult] = useState<any>(null);

  // 일괄 재분석 진행 폴링
  const [batchJobIds, setBatchJobIds] = useState<string[]>(() => {
    try {
      const saved = sessionStorage.getItem(`bidops_batch_${id}`);
      return saved ? JSON.parse(saved) : [];
    } catch { return []; }
  });
  const [batchStatus, setBatchStatus] = useState<any>(null);
  const batchPollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // 실패 항목 필터
  const [failedReqFilter, setFailedReqFilter] = useState<Set<string> | null>(null);
  const failedReasonMap = new Map<string, string>(
    (batchStatus?.failed_jobs || []).map((f: any) => [f.requirement_id, f.error_message || f.error_code || "실패"])
  );

  // 재분석 상태 맵 (requirement별 최근 상태)
  const [reanalyzeMap, setReanalyzeMap] = useState<Record<string, any>>({});

  const loadReanalyzeMap = useCallback(() => {
    requirementApi.reanalyzeStatusMap(id)
      .then((data) => setReanalyzeMap(data?.items || {}))
      .catch(() => setReanalyzeMap({}));
  }, [id]);

  useEffect(() => { loadReanalyzeMap(); }, [loadReanalyzeMap]);

  // batch job ids 변경 시 sessionStorage에 저장
  useEffect(() => {
    try {
      if (batchJobIds.length > 0) sessionStorage.setItem(`bidops_batch_${id}`, JSON.stringify(batchJobIds));
      else sessionStorage.removeItem(`bidops_batch_${id}`);
    } catch {}
  }, [batchJobIds, id]);

  // batch polling
  useEffect(() => {
    if (batchJobIds.length === 0) {
      setBatchStatus(null);
      return;
    }
    const poll = () => {
      requirementApi.batchReanalyzeStatus(id, batchJobIds)
        .then((status) => {
          setBatchStatus(status);
          if (status.done) {
            if (batchPollingRef.current) { clearInterval(batchPollingRef.current); batchPollingRef.current = null; }
            // 완료 후 목록 + 재분석 상태 맵 새로고침
            load();
            loadReanalyzeMap();
          }
        })
        .catch(() => {});
    };
    poll(); // 즉시 1회
    batchPollingRef.current = setInterval(poll, 3000);
    return () => { if (batchPollingRef.current) clearInterval(batchPollingRef.current); };
  }, [batchJobIds, id]);

  const buildParams = useCallback(() => {
    const p = new URLSearchParams();
    if (reviewFilter) p.set("review_status", reviewFilter);
    if (categoryFilter) p.set("category", categoryFilter);
    if (factLevelFilter) p.set("fact_level", factLevelFilter);
    if (mandatoryFilter) p.set("mandatory", mandatoryFilter);
    if (queryNeededFilter) p.set("query_needed", queryNeededFilter);
    if (qualityIssueCode) p.set("quality_issue_code", qualityIssueCode);
    if (qualitySeverity) p.set("quality_severity", qualitySeverity);
    if (keyword) p.set("keyword", keyword);
    p.set("page", String(page));
    p.set("size", "200");
    return p.toString();
  }, [reviewFilter, categoryFilter, factLevelFilter, mandatoryFilter, queryNeededFilter, qualityIssueCode, qualitySeverity, keyword, page]);

  const load = useCallback(() => {
    setLoading(true);
    requirementApi.list(id, buildParams())
      .then((d) => { setItems(d.items || []); setTotalCount(d.total_count || 0); })
      .catch(() => { setItems([]); setTotalCount(0); })
      .finally(() => setLoading(false));
  }, [id, buildParams]);

  useEffect(() => { load(); }, [load]);

  // 전체 집계용 (필터 무관)
  const [coverage, setCoverage] = useState<any>(null);
  useEffect(() => {
    requirementApi.list(id, "size=500")
      .then((d) => setAllItems(d.items || []))
      .catch(() => {});
    // 커버리지 감사 로드 (첫 문서 기준)
    documentApi.list(id).then((d) => {
      const docs = d.items || [];
      if (docs.length > 0) {
        analysisJobApi.coverage(id, docs[0].id)
          .then(setCoverage)
          .catch(() => {});
      }
    }).catch(() => {});
  }, [id]);

  // ── 필터 스냅샷 (프리셋용) ──────────────────────────────────────
  type FilterSnapshot = {
    review_status?: string; category?: string; fact_level?: string;
    mandatory?: string; query_needed?: string; quality_issue_code?: string;
    quality_severity?: string; reanalyze_status?: string; reanalyze_sort?: string;
    keyword?: string;
  };

  const getFilterSnapshot = (): FilterSnapshot => {
    const s: FilterSnapshot = {};
    if (reviewFilter) s.review_status = reviewFilter;
    if (categoryFilter) s.category = categoryFilter;
    if (factLevelFilter) s.fact_level = factLevelFilter;
    if (mandatoryFilter) s.mandatory = mandatoryFilter;
    if (queryNeededFilter) s.query_needed = queryNeededFilter;
    if (qualityIssueCode) s.quality_issue_code = qualityIssueCode;
    if (qualitySeverity) s.quality_severity = qualitySeverity;
    if (reanalyzeFilter) s.reanalyze_status = reanalyzeFilter;
    if (reanalyzeSort) s.reanalyze_sort = reanalyzeSort;
    if (keyword) s.keyword = keyword;
    return s;
  };

  const applyFilterSnapshot = (s: FilterSnapshot) => {
    if (keywordTimerRef.current) clearTimeout(keywordTimerRef.current);
    setReviewFilter(s.review_status || "");
    setCategoryFilter(s.category || "");
    setFactLevelFilter(s.fact_level || "");
    setMandatoryFilter(s.mandatory || "");
    setQueryNeededFilter(s.query_needed || "");
    setQualityIssueCode(s.quality_issue_code || "");
    setQualitySeverity(s.quality_severity || "");
    setReanalyzeFilter(s.reanalyze_status || "");
    setReanalyzeSort(s.reanalyze_sort || "");
    setKeyword(s.keyword || "");
    setSearchInput(s.keyword || "");
    setPage(1);
  };

  const resetFilters = () => applyFilterSnapshot({});

  // ── 프리셋 관리 ─────────────────────────────────────────────────
  type FilterPreset = { name: string; filters: FilterSnapshot };

  const PRESET_KEY = `bidops_presets_${id}`;
  const DEFAULT_PRESETS: FilterPreset[] = [
    { name: "검토필요", filters: { review_status: "NOT_REVIEWED" } },
    { name: "품질 치명 이슈", filters: { quality_severity: "CRITICAL" } },
    { name: "재분석 실패", filters: { reanalyze_status: "FAILED" } },
  ];

  const [presets, setPresets] = useState<FilterPreset[]>(() => {
    try {
      const saved = localStorage.getItem(PRESET_KEY);
      return saved ? JSON.parse(saved) : DEFAULT_PRESETS;
    } catch { return DEFAULT_PRESETS; }
  });
  const [presetNameInput, setPresetNameInput] = useState("");
  const [showPresetSave, setShowPresetSave] = useState(false);

  useEffect(() => {
    try { localStorage.setItem(PRESET_KEY, JSON.stringify(presets)); } catch {}
  }, [presets, PRESET_KEY]);

  const savePreset = () => {
    const name = presetNameInput.trim();
    if (!name) return;
    const snapshot = getFilterSnapshot();
    if (Object.keys(snapshot).length === 0) return;
    setPresets(prev => [...prev.filter(p => p.name !== name), { name, filters: snapshot }]);
    setPresetNameInput("");
    setShowPresetSave(false);
  };

  const deletePreset = (name: string) => {
    setPresets(prev => prev.filter(p => p.name !== name));
  };

  const hasActive = !!(reviewFilter || categoryFilter || factLevelFilter || mandatoryFilter || queryNeededFilter || qualityIssueCode || qualitySeverity || reanalyzeFilter || keyword);

  // reanalyzeMap에서 requirement의 재분석 상태 추출 헬퍼
  const getReanalyzeStatus = (reqId: string): string => {
    const st = reanalyzeMap[reqId];
    if (!st) return "NONE";
    if (st.status === "COMPLETED" && st.cache_hit) return "CACHE_HIT";
    return st.status; // PENDING, RUNNING, COMPLETED, FAILED
  };

  // 클라이언트 필터 + 정렬 적용
  const applyReanalyzeFilterSort = (list: any[]): any[] => {
    let result = list;
    // 필터
    if (reanalyzeFilter) {
      result = result.filter((r: any) => getReanalyzeStatus(r.id) === reanalyzeFilter);
    }
    // 정렬
    if (reanalyzeSort) {
      const ORDER: Record<string, number> = {
        FAILED: 0, RUNNING: 1, PENDING: 2, COMPLETED: 3, CACHE_HIT: 4, NONE: 5,
      };
      result = [...result].sort((a: any, b: any) =>
        (ORDER[getReanalyzeStatus(a.id)] ?? 9) - (ORDER[getReanalyzeStatus(b.id)] ?? 9)
      );
    }
    return result;
  };
  const totalPages = Math.max(1, Math.ceil(totalCount / 30));

  const columns: Column[] = [
    { key: "requirement_code", label: "코드", width: "w-36",
      render: (r) => (
        <div>
          <span className="font-mono text-xs text-gray-600">{r.requirement_code}</span>
          {r.original_req_nos && (
            <span className="ml-1.5 font-mono text-[10px] text-blue-500 bg-blue-50 px-1 py-0.5 rounded">
              {r.original_req_nos}
            </span>
          )}
        </div>
      ) },
    { key: "title", label: "제목",
      render: (r) => <span className="truncate max-w-xs block">{r.title}</span> },
    { key: "category", label: "카테고리", width: "w-24",
      render: (r) => <span className="text-xs text-gray-500">{CATEGORY_LABELS[r.category] || r.category}</span> },
    { key: "mandatory_flag", label: "필수", width: "w-16", align: "center",
      render: (r) => (
        <span className={`text-[11px] font-bold px-1.5 py-0.5 rounded ${
          r.mandatory_flag ? "bg-red-100 text-red-700" : "bg-gray-100 text-gray-400"
        }`}>{r.mandatory_flag ? "필수" : "선택"}</span>
      ) },
    { key: "fact_level", label: "근거", width: "w-20",
      render: (r) => <StatusBadge value={r.fact_level || "REVIEW_NEEDED"} /> },
    { key: "review_status", label: "검토", width: "w-20",
      render: (r) => <StatusBadge value={r.review_status || "NOT_REVIEWED"} /> },
    { key: "query_needed", label: "질의", width: "w-16", align: "center",
      render: (r) => (
        <span className={`text-[11px] font-bold px-1.5 py-0.5 rounded ${
          r.query_needed ? "bg-orange-100 text-orange-700" : "bg-gray-100 text-gray-400"
        }`}>{r.query_needed ? "필요" : "-"}</span>
      ) },
    { key: "extraction_status", label: "추출", width: "w-16",
      render: (r) => {
        if (r.extraction_status === "MERGED") return <span className="text-[10px] bg-purple-100 text-purple-700 px-1 py-0.5 rounded font-bold">병합</span>;
        if (r.extraction_status === "MISSING_CANDIDATE") return <span className="text-[10px] bg-red-100 text-red-700 px-1 py-0.5 rounded font-bold">누락?</span>;
        return <span className="text-[10px] text-gray-400">1:1</span>;
      } },
    { key: "signals", label: "", width: "w-10",
      render: (r) => {
        const flags: string[] = [];
        if (r.mandatory_flag && r.fact_level === "REVIEW_NEEDED") flags.push("!");
        if (r.query_needed) flags.push("?");
        if (r.review_status === "NEEDS_UPDATE") flags.push("△");
        if (r.extraction_status === "MERGED") flags.push("M");
        return flags.length > 0
          ? <span className="text-xs text-red-500 font-bold" title="주의 필요">{flags.join("")}</span>
          : null;
      } },
  ];

  // 전체 프로젝트 기준 집계
  const total = allItems.length;
  const stat = {
    notReviewed: allItems.filter((r: any) => !r.review_status || r.review_status === "NOT_REVIEWED").length,
    inReview: allItems.filter((r: any) => r.review_status === "IN_REVIEW").length,
    approved: allItems.filter((r: any) => r.review_status === "APPROVED").length,
    hold: allItems.filter((r: any) => r.review_status === "HOLD").length,
    needsUpdate: allItems.filter((r: any) => r.review_status === "NEEDS_UPDATE").length,
    mandatory: allItems.filter((r: any) => r.mandatory_flag).length,
    queryNeeded: allItems.filter((r: any) => r.query_needed).length,
    merged: allItems.filter((r: any) => r.extraction_status === "MERGED").length,
    single: allItems.filter((r: any) => !r.extraction_status || r.extraction_status === "SINGLE").length,
  };

  // 원문 요구사항 수 추정: MERGED 항목의 original_req_nos 파싱
  const countOriginalReqs = () => {
    const allNos = new Set<string>();
    allItems.forEach((r: any) => {
      if (r.original_req_nos) {
        r.original_req_nos.split(",").map((s: string) => s.trim()).filter(Boolean).forEach((n: string) => allNos.add(n));
      } else {
        allNos.add(r.requirement_code);
      }
    });
    return allNos.size;
  };
  const estimatedOriginalCount = countOriginalReqs();
  const reviewedCount = stat.approved + stat.hold + stat.needsUpdate + stat.inReview;
  const progressPct = total > 0 ? Math.round((reviewedCount / total) * 100) : 0;
  const firstNotReviewed = allItems.find((r: any) => !r.review_status || r.review_status === "NOT_REVIEWED");

  return (
    <div className="space-y-3">
      {/* 커버리지 감사 요약 */}
      {coverage && coverage.expected_count > 0 && (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-4 space-y-2">
          <div className="flex items-center gap-3">
            <span className="text-xs font-semibold text-gray-600">추출 커버리지</span>
            <div className="flex-1 bg-gray-100 rounded-full h-1.5">
              <div className={`h-1.5 rounded-full transition-all ${coverage.coverage_rate >= 90 ? "bg-emerald-500" : coverage.coverage_rate >= 70 ? "bg-amber-400" : "bg-rose-400"}`}
                style={{ width: `${Math.min(coverage.coverage_rate, 100)}%` }} />
            </div>
            <span className={`text-xs font-bold tabular-nums ${coverage.coverage_rate >= 90 ? "text-emerald-600" : coverage.coverage_rate >= 70 ? "text-amber-600" : "text-rose-600"}`}>
              {coverage.coverage_rate}%
            </span>
          </div>
          <div className="flex gap-4 text-[11px]">
            <span className="text-gray-400">원문 <b className="text-gray-600">{coverage.expected_count}</b></span>
            <span className="text-gray-400">추출 <b className="text-blue-600">{coverage.extracted_count}</b></span>
            <span className="text-gray-400">저장 <b className="text-emerald-600">{coverage.saved_count}</b></span>
            {coverage.merged_count > 0 && <span className="text-violet-500">병합 <b>{coverage.merged_count}</b></span>}
            {coverage.missing_count > 0 && (
              <span className="text-rose-500">누락 <b>{coverage.missing_count}</b>
                {coverage.missing_req_nos && (
                  <span className="text-rose-300 ml-1 font-mono text-[10px]">
                    ({JSON.parse(coverage.missing_req_nos).join(", ")})
                  </span>
                )}
              </span>
            )}
            {coverage.missing_count === 0 && coverage.expected_count > 0 && (
              <span className="text-emerald-500 font-medium">누락 없음</span>
            )}
          </div>
          {coverage.category_summary && (() => {
            try {
              const cats = JSON.parse(coverage.category_summary);
              const entries = Object.entries(cats).filter(([,v]: any) => v.expected > 0);
              if (entries.length === 0) return null;
              return (
                <div className="flex gap-1.5 flex-wrap">
                  {entries.map(([k, v]: any) => (
                    <span key={k} className={`text-[10px] px-1.5 py-0.5 rounded-md border ${v.missing > 0 ? "bg-rose-50/60 text-rose-600 border-rose-100" : "bg-emerald-50/60 text-emerald-600 border-emerald-100"}`}>
                      {k} {v.matched}/{v.expected} {v.missing > 0 && `(-${v.missing})`}
                    </span>
                  ))}
                </div>
              );
            } catch { return null; }
          })()}
        </div>
      )}

      {/* 검토 진행률 + 요약 카드 */}
      {total > 0 && (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-4 space-y-3">
          {/* 진행률 바 */}
          <div className="flex items-center gap-3">
            <span className="text-xs font-semibold text-gray-600 shrink-0">검토 진행률</span>
            <div className="flex-1 bg-gray-100 rounded-full h-1.5">
              <div className="h-1.5 rounded-full transition-all duration-500 bg-gradient-to-r from-indigo-400 to-emerald-400"
                style={{ width: `${progressPct}%` }} />
            </div>
            <span className="text-xs font-bold text-gray-600 shrink-0 min-w-[40px] text-right tabular-nums">{progressPct}%</span>
          </div>

          {/* 추출 커버리지 */}
          {(stat.merged > 0 || estimatedOriginalCount > total) && (
            <div className="flex items-center gap-3 bg-violet-50/50 border border-violet-100 rounded-lg px-3 py-2 text-[11px]">
              <span className="text-violet-600 font-medium">커버리지</span>
              <span className="text-violet-500">원문 {estimatedOriginalCount} → 추출 {total}</span>
              {stat.merged > 0 && <span className="bg-violet-100 text-violet-700 px-1.5 py-0.5 rounded-md font-semibold">병합 {stat.merged}</span>}
            </div>
          )}

          {/* 상태별 카드 */}
          <div className="grid grid-cols-3 md:grid-cols-6 gap-1.5">
            <StatCard label="전체" count={total} color="bg-gray-50 text-gray-600" onClick={resetFilters} />
            <StatCard label="미검토" count={stat.notReviewed} color="bg-slate-50 text-slate-600"
              active={reviewFilter === "NOT_REVIEWED"}
              onClick={() => { setReviewFilter("NOT_REVIEWED"); setPage(1); }} />
            <StatCard label="승인" count={stat.approved} color="bg-emerald-50 text-emerald-600"
              active={reviewFilter === "APPROVED"}
              onClick={() => { setReviewFilter("APPROVED"); setPage(1); }} />
            <StatCard label="수정필요" count={stat.needsUpdate} color="bg-rose-50 text-rose-600"
              active={reviewFilter === "NEEDS_UPDATE"}
              onClick={() => { setReviewFilter("NEEDS_UPDATE"); setPage(1); }} />
            <StatCard label="필수" count={stat.mandatory} color="bg-rose-50/60 text-rose-600"
              active={mandatoryFilter === "true"}
              onClick={() => { setMandatoryFilter(mandatoryFilter === "true" ? "" : "true"); setPage(1); }} />
            <StatCard label="질의필요" count={stat.queryNeeded} color="bg-amber-50 text-amber-600"
              active={queryNeededFilter === "true"}
              onClick={() => { setQueryNeededFilter(queryNeededFilter === "true" ? "" : "true"); setPage(1); }} />
          </div>

          {/* 빠른 액션 */}
          <div className="flex gap-2 flex-wrap">
            {stat.notReviewed > 0 && firstNotReviewed && (
              <button onClick={() => router.push(`/projects/${id}/requirements/${firstNotReviewed.id}?mode=review`)}
                className="text-[11px] px-3 py-1.5 rounded-lg font-medium bg-indigo-600 text-white hover:bg-indigo-700 transition-colors shadow-sm">
                미검토 연속 검토 ({stat.notReviewed}건)
              </button>
            )}
            {stat.queryNeeded > 0 && (
              <button onClick={() => { setQueryNeededFilter("true"); setReviewFilter(""); setPage(1); }}
                className="text-[11px] px-3 py-1.5 rounded-lg font-medium bg-amber-50 text-amber-700 border border-amber-100 hover:bg-amber-100 transition-colors">
                질의 필요 항목
              </button>
            )}
            {stat.needsUpdate > 0 && (
              <button onClick={() => { setReviewFilter("NEEDS_UPDATE"); setPage(1); }}
                className="text-[11px] px-3 py-1.5 rounded-lg font-medium bg-rose-50 text-rose-600 border border-rose-100 hover:bg-rose-100 transition-colors">
                수정 필요 항목
              </button>
            )}
            {stat.queryNeeded > 0 && (
              <Link href={`/projects/${id}/inquiries`}
                className="text-[11px] px-3 py-1.5 rounded-lg font-medium bg-violet-50 text-violet-600 border border-violet-100 hover:bg-violet-100 transition-colors">
                질의리스트
              </Link>
            )}
            {progressPct === 100 && (
              <span className="text-[11px] px-3 py-1.5 rounded-lg font-medium bg-emerald-50 text-emerald-600 border border-emerald-100">
                검토 완료
              </span>
            )}
          </div>
        </div>
      )}

      <FilterBar
        toggleFilters={[{
          label: "검토상태", options: REVIEW_FILTERS,
          value: reviewFilter, onChange: (v) => { setReviewFilter(v); setPage(1); },
        }]}
        selectFilters={[
          { label: "카테고리", options: CATEGORY_OPTIONS, value: categoryFilter, onChange: (v) => { setCategoryFilter(v); setPage(1); } },
          { label: "근거수준", options: FACT_OPTIONS, value: factLevelFilter, onChange: (v) => { setFactLevelFilter(v); setPage(1); } },
          { label: "필수여부", options: MANDATORY_OPTIONS, value: mandatoryFilter, onChange: (v) => { setMandatoryFilter(v); setPage(1); } },
          { label: "질의필요", options: QUERY_OPTIONS, value: queryNeededFilter, onChange: (v) => { setQueryNeededFilter(v); setPage(1); } },
        ]}
        keyword={{ value: searchInput, onChange: setSearchInput, onSearch: applyKeywordNow, placeholder: "키워드 검색..." }}
        hasActiveFilter={hasActive} onReset={resetFilters}
        totalCount={totalCount}
      />

      {/* ── 통합 필터 칩 + 재분석 툴바 ─────────────────────────────── */}
      {/* ── 프리셋 바 ───────────────────────────────────────────── */}
      <div className="flex items-center gap-1.5 mb-1.5 flex-wrap text-[11px]">
        <span className="text-gray-400 mr-0.5">프리셋:</span>
        {presets.map(p => (
          <span key={p.name} className="inline-flex items-center gap-0.5">
            <button onClick={() => applyFilterSnapshot(p.filters)}
              className="px-2 py-0.5 rounded bg-white border border-gray-200 text-gray-600 hover:bg-indigo-50 hover:border-indigo-300 hover:text-indigo-700 transition-colors">
              {p.name}
            </button>
            {!DEFAULT_PRESETS.some(d => d.name === p.name) && (
              <button onClick={() => deletePreset(p.name)}
                className="text-gray-300 hover:text-red-500 text-[9px]">&times;</button>
            )}
          </span>
        ))}
        {hasActive && !showPresetSave && (
          <button onClick={() => setShowPresetSave(true)}
            className="px-2 py-0.5 rounded border border-dashed border-gray-300 text-gray-400 hover:border-indigo-400 hover:text-indigo-600 transition-colors">
            + 현재 필터 저장
          </button>
        )}
        {showPresetSave && (
          <span className="inline-flex items-center gap-1">
            <input value={presetNameInput} onChange={e => setPresetNameInput(e.target.value)}
              onKeyDown={e => e.key === "Enter" && savePreset()}
              placeholder="프리셋 이름"
              className="border rounded px-1.5 py-0.5 text-[11px] w-28" autoFocus />
            <button onClick={savePreset}
              className="px-1.5 py-0.5 rounded bg-indigo-600 text-white hover:bg-indigo-700 text-[11px]">저장</button>
            <button onClick={() => { setShowPresetSave(false); setPresetNameInput(""); }}
              className="text-gray-400 hover:text-gray-600">&times;</button>
          </span>
        )}
      </div>

      <ActiveFilterChips
        filters={[
          reviewFilter && { label: `검토: ${REVIEW_FILTERS.find(f => f.value === reviewFilter)?.label || reviewFilter}`, color: "bg-blue-100 text-blue-700", onRemove: () => { setReviewFilter(""); setPage(1); } },
          categoryFilter && { label: `카테고리: ${CATEGORY_LABELS[categoryFilter] || categoryFilter}`, color: "bg-gray-100 text-gray-700", onRemove: () => { setCategoryFilter(""); setPage(1); } },
          factLevelFilter && { label: `근거: ${factLevelFilter === "FACT" ? "확정" : factLevelFilter === "INFERENCE" ? "추론" : "검토필요"}`, color: "bg-yellow-100 text-yellow-700", onRemove: () => { setFactLevelFilter(""); setPage(1); } },
          mandatoryFilter && { label: mandatoryFilter === "true" ? "필수" : "선택", color: "bg-rose-100 text-rose-700", onRemove: () => { setMandatoryFilter(""); setPage(1); } },
          queryNeededFilter && { label: queryNeededFilter === "true" ? "질의필요" : "질의불필요", color: "bg-orange-100 text-orange-700", onRemove: () => { setQueryNeededFilter(""); setPage(1); } },
          qualitySeverity && { label: `품질: ${qualitySeverity === "CRITICAL" ? "치명" : "일반"}`, color: qualitySeverity === "CRITICAL" ? "bg-red-100 text-red-700" : "bg-amber-100 text-amber-700", onRemove: () => { setQualitySeverity(""); setPage(1); } },
          qualityIssueCode && { label: qualityIssueCode, color: "bg-purple-100 text-purple-700 font-mono", onRemove: () => { setQualityIssueCode(""); setPage(1); } },
          reanalyzeFilter && { label: `재분석: ${{ FAILED: "실패", RUNNING: "진행중", PENDING: "대기", COMPLETED: "완료", CACHE_HIT: "캐시", NONE: "없음" }[reanalyzeFilter] || reanalyzeFilter}`, color: reanalyzeFilter === "FAILED" ? "bg-red-100 text-red-700" : "bg-indigo-100 text-indigo-700", onRemove: () => { setReanalyzeFilter(""); setPage(1); } },
          reanalyzeSort && { label: "실패 우선 정렬", color: "bg-indigo-100 text-indigo-700", onRemove: () => setReanalyzeSort("") },
          keyword && { label: `"${keyword}"`, color: "bg-gray-100 text-gray-700", onRemove: () => { if (keywordTimerRef.current) clearTimeout(keywordTimerRef.current); setKeyword(""); setSearchInput(""); setPage(1); } },
          failedReqFilter && { label: `실패 항목만 (${failedReqFilter.size}건)`, color: "bg-red-100 text-red-700", onRemove: () => setFailedReqFilter(null) },
        ].filter(Boolean) as { label: string; color: string; onRemove: () => void }[]}
        onResetAll={resetFilters}
      />

      {/* 재분석 상태 바 */}
      <div className="flex items-center gap-1.5 mb-2 flex-wrap text-[11px]">
        <span className="text-gray-400 mr-0.5">재분석:</span>
        {[
          { value: "", label: "전체" },
          { value: "FAILED", label: "실패", c: "bg-red-100 text-red-700" },
          { value: "RUNNING", label: "진행중", c: "bg-blue-100 text-blue-700" },
          { value: "PENDING", label: "대기", c: "bg-blue-50 text-blue-600" },
          { value: "COMPLETED", label: "완료", c: "bg-green-100 text-green-700" },
          { value: "CACHE_HIT", label: "캐시", c: "bg-gray-100 text-gray-600" },
          { value: "NONE", label: "없음", c: "bg-gray-50 text-gray-400" },
        ].map(o => (
          <button key={o.value} onClick={() => { setReanalyzeFilter(o.value); setPage(1); }}
            className={`px-1.5 py-0.5 rounded transition-colors ${
              reanalyzeFilter === o.value ? "ring-1 ring-indigo-400 font-bold " + (o.c || "bg-gray-200 text-gray-700") : (o.c || "bg-gray-100 text-gray-500") + " hover:opacity-80"
            }`}>{o.label}</button>
        ))}
        <span className="text-gray-200 mx-0.5">|</span>
        <button onClick={() => setReanalyzeSort(reanalyzeSort ? "" : "failed_first")}
          className={`px-1.5 py-0.5 rounded transition-colors ${reanalyzeSort ? "bg-indigo-100 text-indigo-700 ring-1 ring-indigo-300 font-bold" : "bg-gray-100 text-gray-400 hover:bg-gray-200"}`}>
          {reanalyzeSort ? "실패우선 ON" : "실패우선"}
        </button>
      </div>

      {/* 실패 항목 재재분석 액션 */}
      {failedReqFilter && failedReqFilter.size > 0 && (
        <div className="flex items-center gap-2 mb-2 text-xs">
          <button onClick={() => setSelectedIds(new Set(failedReqFilter))}
            className="px-2 py-0.5 rounded bg-red-100 text-red-700 font-medium hover:bg-red-200">
            실패 항목 전체 선택
          </button>
          {selectedIds.size > 0 && [...selectedIds].every(sid => failedReqFilter.has(sid)) && (
            <button onClick={async () => {
              setBatchState("requesting"); setBatchResult(null);
              try {
                const result = await requirementApi.batchReanalyze(id, Array.from(selectedIds));
                setBatchResult(result); setBatchState("done"); setSelectedIds(new Set()); setFailedReqFilter(null);
                if (result.created_job_ids?.length > 0) setBatchJobIds(result.created_job_ids);
              } catch (err: any) { setBatchResult({ error: err.message || "재재분석 실패" }); setBatchState("done"); }
            }} disabled={batchState === "requesting"}
              className={`px-2 py-0.5 rounded font-medium transition-colors ${batchState === "requesting" ? "bg-gray-200 text-gray-500 cursor-wait" : "bg-indigo-600 text-white hover:bg-indigo-700"}`}>
              {batchState === "requesting" ? "요청 중..." : `선택 실패 항목 재분석 (${selectedIds.size}건)`}
            </button>
          )}
        </div>
      )}

      {/* 일괄 재분석 진행 배너 */}
      {batchStatus && batchJobIds.length > 0 && (
        <div className={`flex items-center gap-3 px-4 py-2.5 rounded-xl border text-[11px] ${
          batchStatus.done
            ? batchStatus.failed_count > 0
              ? "bg-amber-50/60 border-amber-100"
              : "bg-emerald-50/60 border-emerald-100"
            : "bg-blue-50/60 border-blue-100"
        }`}>
          <span className="font-medium text-gray-700">
            {batchStatus.done ? "일괄 재분석 완료" : "일괄 재분석 진행 중"}
          </span>
          <span className="text-gray-500">총 {batchStatus.total_jobs}건</span>
          {!batchStatus.done && (
            <>
              {batchStatus.running_count > 0 && <span className="text-blue-600 font-bold">진행 {batchStatus.running_count}</span>}
              {batchStatus.pending_count > 0 && <span className="text-gray-500">대기 {batchStatus.pending_count}</span>}
              <span className="animate-pulse text-blue-400">...</span>
            </>
          )}
          {batchStatus.completed_count > 0 && (
            <span className="text-green-600 font-bold">완료 {batchStatus.completed_count}</span>
          )}
          {batchStatus.cache_hit_count > 0 && (
            <span className="text-gray-500">캐시 {batchStatus.cache_hit_count}</span>
          )}
          {batchStatus.failed_count > 0 && (
            <button onClick={() => {
              const ids = new Set<string>((batchStatus.failed_jobs || []).map((f: any) => f.requirement_id as string));
              setFailedReqFilter(failedReqFilter ? null : ids);
            }}
              className={`font-bold px-1.5 py-0.5 rounded transition-colors ${
                failedReqFilter ? "bg-red-200 text-red-800 ring-1 ring-red-400" : "text-red-600 hover:bg-red-100"
              }`}>
              실패 {batchStatus.failed_count}
            </button>
          )}
          {/* 진행률 바 */}
          {!batchStatus.done && batchStatus.total_jobs > 0 && (
            <div className="flex-1 bg-gray-200 rounded-full h-1.5 min-w-[60px]">
              <div className="bg-blue-500 h-1.5 rounded-full transition-all"
                style={{ width: `${Math.round(((batchStatus.completed_count + batchStatus.failed_count) / batchStatus.total_jobs) * 100)}%` }} />
            </div>
          )}
          {batchStatus.done && (
            <button onClick={() => { setBatchJobIds([]); setBatchStatus(null); setFailedReqFilter(null); }}
              className="text-gray-400 hover:text-gray-600 ml-auto">&times;</button>
          )}
        </div>
      )}

      {/* 일괄 재분석 액션 바 */}
      {items.length > 0 && (
        <div className="flex items-center gap-2 mb-2 text-xs">
          <label className="flex items-center gap-1 cursor-pointer text-gray-500">
            <input type="checkbox"
              checked={items.length > 0 && selectedIds.size === items.length}
              onChange={(e) => {
                if (e.target.checked) setSelectedIds(new Set(items.map((r: any) => r.id)));
                else setSelectedIds(new Set());
              }}
              className="w-3.5 h-3.5" />
            전체 선택 ({selectedIds.size}/{items.length})
          </label>
          {selectedIds.size > 0 && (
            <button onClick={async () => {
              setBatchState("requesting");
              setBatchResult(null);
              try {
                const result = await requirementApi.batchReanalyze(id, Array.from(selectedIds));
                setBatchResult(result);
                setBatchState("done");
                setSelectedIds(new Set());
                // 생성된 job이 있으면 polling 시작
                if (result.created_job_ids?.length > 0) {
                  setBatchJobIds(result.created_job_ids);
                }
              } catch (err: any) {
                setBatchResult({ error: err.message || "일괄 재분석 실패" });
                setBatchState("done");
              }
            }}
              disabled={batchState === "requesting"}
              className={`px-3 py-1 rounded font-medium transition-colors ${
                batchState === "requesting"
                  ? "bg-gray-200 text-gray-500 cursor-wait"
                  : "bg-indigo-600 text-white hover:bg-indigo-700"
              }`}>
              {batchState === "requesting" ? "재분석 요청 중..." : `선택 항목 재분석 (${selectedIds.size}건)`}
            </button>
          )}
          {batchResult && batchState === "done" && (
            <div className={`flex items-center gap-2 px-2.5 py-1 rounded border text-[11px] ${
              batchResult.error ? "bg-red-50 border-red-200 text-red-700" : "bg-green-50 border-green-200 text-green-700"
            }`}>
              {batchResult.error ? (
                <span>{batchResult.error}</span>
              ) : (
                <>
                  <span>생성 {batchResult.created_job_count}건</span>
                  {batchResult.skipped_count > 0 && <span className="text-amber-600">제외 {batchResult.skipped_count}건</span>}
                </>
              )}
              <button onClick={() => { setBatchResult(null); setBatchState("idle"); }}
                className="text-gray-400 hover:text-gray-600">&times;</button>
            </div>
          )}
        </div>
      )}

      <GroupedRequirementList items={applyReanalyzeFilterSort(failedReqFilter ? items.filter((r: any) => failedReqFilter.has(r.id)) : items)} loading={loading}
        failedReasonMap={failedReasonMap}
        reanalyzeMap={reanalyzeMap}
        selectedIds={selectedIds}
        onToggleSelect={(reqId: string) => {
          setSelectedIds(prev => {
            const next = new Set(prev);
            if (next.has(reqId)) next.delete(reqId); else next.add(reqId);
            return next;
          });
        }}
        onRowClick={(r: any) => router.push(`/projects/${id}/requirements/${r.id}`)} />

      {totalPages > 1 && (
        <div className="flex justify-center gap-1 mt-4" role="navigation" aria-label="페이지네이션">
          <button onClick={() => setPage(Math.max(1, page - 1))} disabled={page <= 1}
            className="px-3 py-1 text-xs border rounded disabled:opacity-30" aria-label="이전 페이지">이전</button>
          {Array.from({ length: Math.min(totalPages, 10) }, (_, i) => {
            const start = Math.max(1, Math.min(page - 4, totalPages - 9));
            const p = start + i;
            if (p > totalPages) return null;
            return (
              <button key={p} onClick={() => setPage(p)} aria-current={page === p ? "page" : undefined}
                className={`px-3 py-1 text-xs border rounded ${page === p ? "bg-blue-600 text-white border-blue-600" : "hover:bg-gray-50"}`}>
                {p}
              </button>
            );
          })}
          <button onClick={() => setPage(Math.min(totalPages, page + 1))} disabled={page >= totalPages}
            className="px-3 py-1 text-xs border rounded disabled:opacity-30" aria-label="다음 페이지">다음</button>
        </div>
      )}
    </div>
  );
}

const CATEGORY_LABELS_SHORT: Record<string, string> = {
  BUSINESS_OVERVIEW: "사업개요", BACKGROUND: "배경", OBJECTIVE: "목표", SCOPE: "범위",
  FUNCTIONAL: "기능", NON_FUNCTIONAL: "비기능", PERFORMANCE: "성능", SECURITY: "보안",
  QUALITY: "품질", TESTING: "시험", DATA_INTEGRATION: "데이터", UI_UX: "UI/UX",
  INFRASTRUCTURE: "인프라", PERSONNEL: "인력", TRACK_RECORD: "실적", SCHEDULE: "일정",
  DELIVERABLE: "산출물", SUBMISSION: "제출", PROPOSAL_GUIDE: "제안안내", EVALUATION: "평가",
  PRESENTATION: "발표", MAINTENANCE: "유지보수", TRAINING: "교육", LEGAL: "법률", ETC: "기타",
};

function GroupedRequirementList({ items, loading, onRowClick, selectedIds, onToggleSelect, failedReasonMap, reanalyzeMap }: {
  items: any[]; loading: boolean; onRowClick: (r: any) => void;
  selectedIds?: Set<string>; onToggleSelect?: (id: string) => void;
  failedReasonMap?: Map<string, string>;
  reanalyzeMap?: Record<string, any>;
}) {
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());

  if (loading) return <LoadingState variant="list" rows={5} />;
  if (items.length === 0) return <EmptyState title="아직 분석된 요구사항이 없습니다" description="문서 분석이 완료되면 요구사항이 표시됩니다." compact />;

  // 원문번호 정렬 순서
  const PREFIX_ORDER = ["MAR", "DAR", "MHR", "SER", "QUR", "COR", "PMR", "PSR"];

  const sortKey = (key: string) => {
    const match = key.match(/^([A-Z]+)-?(\d+)/);
    if (!match) return [999, 0];
    const idx = PREFIX_ORDER.indexOf(match[1]);
    return [idx >= 0 ? idx : 100, parseInt(match[2] || "0")];
  };

  // 그룹핑: original_req_nos 기준
  const groups: Map<string, any[]> = new Map();
  items.forEach((r) => {
    const key = r.original_req_nos || r.requirement_code;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(r);
  });

  // 그룹 키 정렬
  const sortedGroupEntries = Array.from(groups.entries()).sort((a, b) => {
    const [pa, sa] = sortKey(a[0]);
    const [pb, sb] = sortKey(b[0]);
    return pa !== pb ? pa - pb : sa - sb;
  });

  const toggleGroup = (key: string) => {
    setExpandedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  };

  return (
    <div className="bg-white rounded-xl border border-gray-100 shadow-sm divide-y divide-gray-50">
      {sortedGroupEntries.map(([groupKey, groupItems]) => {
        const isExpanded = expandedGroups.has(groupKey) || groupItems.length === 1;
        const first = groupItems[0];
        const allCategories = [...new Set(groupItems.map((r: any) => CATEGORY_LABELS_SHORT[r.category] || r.category))];

        return (
          <div key={groupKey}>
            {/* 그룹 헤더 */}
            <div className="flex items-center gap-3 px-4 py-3 cursor-pointer hover:bg-indigo-50/30 transition-colors"
              onClick={() => groupItems.length > 1 ? toggleGroup(groupKey) : onRowClick(first)}>
              {groupItems.length > 1 && (
                <button className="text-xs text-gray-400 w-4 shrink-0" onClick={(e) => { e.stopPropagation(); toggleGroup(groupKey); }}>
                  {isExpanded ? "▼" : "▶"}
                </button>
              )}
              {groupItems.length === 1 && onToggleSelect && (
                <input type="checkbox" checked={selectedIds?.has(first.id) || false}
                  onChange={(e) => { e.stopPropagation(); onToggleSelect(first.id); }}
                  className="w-3.5 h-3.5 shrink-0 cursor-pointer" />
              )}
              {groupItems.length === 1 && !onToggleSelect && <span className="w-4 shrink-0" />}

              <span className="font-mono text-[11px] text-blue-600 bg-blue-50 px-1.5 py-0.5 rounded shrink-0">{groupKey}</span>

              {groupItems.length > 1 && (
                <span className="text-[10px] bg-purple-100 text-purple-700 px-1.5 py-0.5 rounded shrink-0">
                  {groupItems.length}건 분해
                </span>
              )}

              <span className="text-sm text-gray-700 truncate flex-1">
                {groupItems.length === 1 ? first.title : first.title?.substring(0, 50) + "..."}
              </span>

              <span className="text-[10px] text-gray-400 shrink-0">{allCategories.join(", ")}</span>

              {first.mandatory_flag && <span className="text-[10px] bg-red-100 text-red-700 px-1 py-0.5 rounded shrink-0">필수</span>}
              <ReanalyzeBadge reqId={first.id} reanalyzeMap={reanalyzeMap} failedReasonMap={failedReasonMap} />
              <StatusBadge value={first.fact_level || "REVIEW_NEEDED"} />
            </div>

            {/* 펼쳐진 atomic 항목들 */}
            {isExpanded && groupItems.length > 1 && (
              <div className="bg-gray-50/50">
                {groupItems.map((r: any, i: number) => (
                  <div key={r.id} className="flex items-center gap-3 px-4 py-2.5 pl-12 border-t border-gray-50 cursor-pointer hover:bg-indigo-50/20 transition-colors"
                    onClick={() => onRowClick(r)}>
                    {onToggleSelect && (
                      <input type="checkbox" checked={selectedIds?.has(r.id) || false}
                        onChange={(e) => { e.stopPropagation(); onToggleSelect(r.id); }}
                        className="w-3.5 h-3.5 shrink-0 cursor-pointer" />
                    )}
                    <span className="font-mono text-[10px] text-gray-500 w-16 shrink-0">{r.requirement_code}</span>
                    <span className="text-xs text-gray-700 truncate flex-1">{r.title}</span>
                    <span className="text-[10px] text-gray-400 shrink-0">{CATEGORY_LABELS_SHORT[r.category] || r.category}</span>
                    {r.mandatory_flag && <span className="text-[10px] bg-red-100 text-red-700 px-1 py-0.5 rounded shrink-0">필수</span>}
                    {r.query_needed && <span className="text-[10px] bg-orange-100 text-orange-700 px-1 py-0.5 rounded shrink-0">질의</span>}
                    <ReanalyzeBadge reqId={r.id} reanalyzeMap={reanalyzeMap} failedReasonMap={failedReasonMap} />
                    <StatusBadge value={r.fact_level || "REVIEW_NEEDED"} />
                    <StatusBadge value={r.review_status || "NOT_REVIEWED"} />
                  </div>
                ))}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

function ActiveFilterChips({ filters, onResetAll }: {
  filters: { label: string; color: string; onRemove: () => void }[];
  onResetAll: () => void;
}) {
  if (filters.length === 0) return null;
  return (
    <div className="flex items-center gap-1.5 mb-2 flex-wrap text-[11px]">
      <span className="text-gray-400 mr-0.5">필터:</span>
      {filters.map((f, i) => (
        <span key={i} className={`inline-flex items-center gap-1 px-2 py-0.5 rounded ${f.color}`}>
          {f.label}
          <button onClick={f.onRemove} className="text-gray-400 hover:text-gray-700 ml-0.5">&times;</button>
        </span>
      ))}
      {filters.length >= 2 && (
        <button onClick={onResetAll} className="text-blue-600 hover:underline ml-1">전체 초기화</button>
      )}
    </div>
  );
}

function ReanalyzeBadge({ reqId, reanalyzeMap, failedReasonMap }: {
  reqId: string; reanalyzeMap?: Record<string, any>; failedReasonMap?: Map<string, string>;
}) {
  // batch 실패 맵이 우선 (최신 batch 결과)
  if (failedReasonMap?.has(reqId)) {
    return (
      <span className="text-[10px] bg-red-50 text-red-600 px-1.5 py-0.5 rounded border border-red-200 shrink-0"
        title={failedReasonMap.get(reqId)}>
        재분석 실패
      </span>
    );
  }

  const st = reanalyzeMap?.[reqId];
  if (!st) return null;

  if (st.status === "PENDING" || st.status === "RUNNING") {
    return (
      <span className="text-[10px] bg-blue-50 text-blue-600 px-1.5 py-0.5 rounded border border-blue-200 shrink-0 animate-pulse">
        재분석 {st.progress || 0}%
      </span>
    );
  }
  if (st.status === "COMPLETED" && st.cache_hit) {
    return (
      <span className="text-[10px] bg-gray-50 text-gray-500 px-1.5 py-0.5 rounded border border-gray-200 shrink-0">
        캐시
      </span>
    );
  }
  if (st.status === "COMPLETED") {
    return (
      <span className="text-[10px] bg-green-50 text-green-600 px-1.5 py-0.5 rounded border border-green-200 shrink-0">
        재분석 완료
      </span>
    );
  }
  if (st.status === "FAILED") {
    return (
      <span className="text-[10px] bg-red-50 text-red-600 px-1.5 py-0.5 rounded border border-red-200 shrink-0"
        title={st.error_message || st.error_code || "실패"}>
        재분석 실패
      </span>
    );
  }
  return null;
}

function StatCard({ label, count, color, active, onClick }: {
  label: string; count: number; color: string; active?: boolean; onClick?: () => void;
}) {
  return (
    <button onClick={onClick}
      className={`rounded-xl px-3 py-2 text-center transition-all border ${color} ${
        active ? "ring-2 ring-indigo-300 border-indigo-200 shadow-sm" : "border-transparent hover:shadow-sm hover:border-gray-100"
      }`}>
      <div className="text-base font-bold tabular-nums">{count}</div>
      <div className="text-[10px] text-gray-400">{label}</div>
    </button>
  );
}
