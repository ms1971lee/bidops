"use client";

import { useParams, useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useEffect, useState, useCallback } from "react";
import { requirementApi, analysisJobApi, documentApi } from "@/lib/api";
import StatusBadge from "@/components/common/StatusBadge";
import DataTable, { type Column } from "@/components/common/DataTable";
import FilterBar from "@/components/common/FilterBar";

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
  const [keyword, setKeyword] = useState(searchParams.get("keyword") || "");
  const [searchInput, setSearchInput] = useState(searchParams.get("keyword") || "");
  const [page, setPage] = useState(1);

  const buildParams = useCallback(() => {
    const p = new URLSearchParams();
    if (reviewFilter) p.set("review_status", reviewFilter);
    if (categoryFilter) p.set("category", categoryFilter);
    if (factLevelFilter) p.set("fact_level", factLevelFilter);
    if (mandatoryFilter) p.set("mandatory", mandatoryFilter);
    if (queryNeededFilter) p.set("query_needed", queryNeededFilter);
    if (keyword) p.set("keyword", keyword);
    p.set("page", String(page));
    p.set("size", "200");
    return p.toString();
  }, [reviewFilter, categoryFilter, factLevelFilter, mandatoryFilter, queryNeededFilter, keyword, page]);

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

  const resetFilters = () => {
    setReviewFilter(""); setCategoryFilter(""); setFactLevelFilter("");
    setMandatoryFilter(""); setQueryNeededFilter("");
    setKeyword(""); setSearchInput(""); setPage(1);
  };

  const hasActive = !!(reviewFilter || categoryFilter || factLevelFilter || mandatoryFilter || queryNeededFilter || keyword);
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
    <div>
      {/* 커버리지 감사 요약 */}
      {coverage && coverage.expected_count > 0 && (
        <div className="bg-white border rounded-lg p-4 mb-4 space-y-2">
          <div className="flex items-center gap-3">
            <span className="text-sm font-semibold text-gray-700">추출 커버리지</span>
            <div className="flex-1 bg-gray-200 rounded-full h-2">
              <div className={`h-2 rounded-full transition-all ${coverage.coverage_rate >= 90 ? "bg-green-500" : coverage.coverage_rate >= 70 ? "bg-yellow-500" : "bg-red-500"}`}
                style={{ width: `${Math.min(coverage.coverage_rate, 100)}%` }} />
            </div>
            <span className={`text-sm font-bold ${coverage.coverage_rate >= 90 ? "text-green-700" : coverage.coverage_rate >= 70 ? "text-yellow-700" : "text-red-700"}`}>
              {coverage.coverage_rate}%
            </span>
          </div>
          <div className="flex gap-4 text-xs">
            <span className="text-gray-500">원문 <b className="text-gray-800">{coverage.expected_count}</b>건</span>
            <span className="text-gray-500">추출 <b className="text-blue-700">{coverage.extracted_count}</b>건</span>
            <span className="text-gray-500">저장 <b className="text-green-700">{coverage.saved_count}</b>건</span>
            {coverage.merged_count > 0 && <span className="text-purple-600">병합 <b>{coverage.merged_count}</b>건</span>}
            {coverage.missing_count > 0 && (
              <span className="text-red-600">누락 <b>{coverage.missing_count}</b>건
                {coverage.missing_req_nos && (
                  <span className="text-red-400 ml-1 font-mono text-[10px]">
                    ({JSON.parse(coverage.missing_req_nos).join(", ")})
                  </span>
                )}
              </span>
            )}
            {coverage.missing_count === 0 && coverage.expected_count > 0 && (
              <span className="text-green-600 font-medium">누락 없음</span>
            )}
          </div>
          {coverage.category_summary && (() => {
            try {
              const cats = JSON.parse(coverage.category_summary);
              const entries = Object.entries(cats).filter(([,v]: any) => v.expected > 0);
              if (entries.length === 0) return null;
              return (
                <div className="flex gap-2 flex-wrap">
                  {entries.map(([k, v]: any) => (
                    <span key={k} className={`text-[10px] px-2 py-0.5 rounded ${v.missing > 0 ? "bg-red-50 text-red-700" : "bg-green-50 text-green-700"}`}>
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
        <div className="bg-white border rounded-lg p-4 mb-4 space-y-3">
          {/* 진행률 바 */}
          <div className="flex items-center gap-3">
            <span className="text-sm font-semibold text-gray-700 shrink-0">검토 진행률</span>
            <div className="flex-1 bg-gray-200 rounded-full h-2.5">
              <div className="h-2.5 rounded-full transition-all duration-500 bg-gradient-to-r from-blue-500 to-green-500"
                style={{ width: `${progressPct}%` }} />
            </div>
            <span className="text-sm font-bold text-gray-700 shrink-0 min-w-[48px] text-right">{progressPct}%</span>
          </div>

          {/* 추출 커버리지 */}
          {(stat.merged > 0 || estimatedOriginalCount > total) && (
            <div className="flex items-center gap-3 bg-purple-50 rounded-lg px-3 py-2 text-xs">
              <span className="text-purple-700 font-medium">추출 커버리지</span>
              <span className="text-purple-600">원문 약 {estimatedOriginalCount}건 → 추출 {total}건</span>
              {stat.merged > 0 && <span className="bg-purple-200 text-purple-800 px-1.5 py-0.5 rounded font-bold">병합 {stat.merged}건</span>}
              <span className="text-purple-400">1:1 {stat.single}건</span>
            </div>
          )}

          {/* 상태별 카드 */}
          <div className="grid grid-cols-3 md:grid-cols-6 gap-2">
            <StatCard label="전체" count={total} color="bg-gray-50 text-gray-700" onClick={resetFilters} />
            <StatCard label="미검토" count={stat.notReviewed} color="bg-slate-50 text-slate-700"
              active={reviewFilter === "NOT_REVIEWED"}
              onClick={() => { setReviewFilter("NOT_REVIEWED"); setPage(1); }} />
            <StatCard label="승인" count={stat.approved} color="bg-green-50 text-green-700"
              active={reviewFilter === "APPROVED"}
              onClick={() => { setReviewFilter("APPROVED"); setPage(1); }} />
            <StatCard label="수정필요" count={stat.needsUpdate} color="bg-red-50 text-red-700"
              active={reviewFilter === "NEEDS_UPDATE"}
              onClick={() => { setReviewFilter("NEEDS_UPDATE"); setPage(1); }} />
            <StatCard label="필수" count={stat.mandatory} color="bg-rose-50 text-rose-700"
              active={mandatoryFilter === "true"}
              onClick={() => { setMandatoryFilter(mandatoryFilter === "true" ? "" : "true"); setPage(1); }} />
            <StatCard label="질의필요" count={stat.queryNeeded} color="bg-orange-50 text-orange-700"
              active={queryNeededFilter === "true"}
              onClick={() => { setQueryNeededFilter(queryNeededFilter === "true" ? "" : "true"); setPage(1); }} />
          </div>

          {/* 빠른 액션 */}
          <div className="flex gap-2 flex-wrap">
            {stat.notReviewed > 0 && firstNotReviewed && (
              <button onClick={() => router.push(`/projects/${id}/requirements/${firstNotReviewed.id}?mode=review`)}
                className="text-xs px-3 py-1.5 rounded font-medium bg-blue-600 text-white hover:bg-blue-700 transition-colors">
                미검토 연속 검토 ({stat.notReviewed}건)
              </button>
            )}
            {stat.queryNeeded > 0 && (
              <button onClick={() => { setQueryNeededFilter("true"); setReviewFilter(""); setPage(1); }}
                className="text-xs px-3 py-1.5 rounded font-medium bg-orange-100 text-orange-700 hover:bg-orange-200 transition-colors">
                질의 필요 항목 보기
              </button>
            )}
            {stat.needsUpdate > 0 && (
              <button onClick={() => { setReviewFilter("NEEDS_UPDATE"); setPage(1); }}
                className="text-xs px-3 py-1.5 rounded font-medium bg-red-100 text-red-700 hover:bg-red-200 transition-colors">
                수정 필요 항목 보기
              </button>
            )}
            {stat.queryNeeded > 0 && (
              <Link href={`/projects/${id}/inquiries`}
                className="text-xs px-3 py-1.5 rounded font-medium bg-purple-100 text-purple-700 hover:bg-purple-200 transition-colors">
                질의리스트 관리
              </Link>
            )}
            {progressPct === 100 && (
              <span className="text-xs px-3 py-1.5 rounded font-medium bg-green-100 text-green-700">
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
        keyword={{ value: searchInput, onChange: setSearchInput, onSearch: () => { setPage(1); setKeyword(searchInput); }, placeholder: "키워드 검색..." }}
        hasActiveFilter={hasActive} onReset={resetFilters}
        totalCount={totalCount}
      />

      {/* 그룹형 뷰: 같은 original_req_nos 기준 접기/펴기 */}
      <GroupedRequirementList items={items} loading={loading}
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

function GroupedRequirementList({ items, loading, onRowClick }: { items: any[]; loading: boolean; onRowClick: (r: any) => void }) {
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());

  if (loading) return <div className="bg-white rounded-lg border p-8 text-center text-gray-400 text-sm">로딩 중...</div>;
  if (items.length === 0) return <div className="bg-white rounded-lg border p-8 text-center text-gray-400 text-sm">요구사항이 없습니다</div>;

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
    <div className="bg-white rounded-lg border divide-y">
      {sortedGroupEntries.map(([groupKey, groupItems]) => {
        const isExpanded = expandedGroups.has(groupKey) || groupItems.length === 1;
        const first = groupItems[0];
        const allCategories = [...new Set(groupItems.map((r: any) => CATEGORY_LABELS_SHORT[r.category] || r.category))];

        return (
          <div key={groupKey}>
            {/* 그룹 헤더 */}
            <div className={`flex items-center gap-3 px-4 py-2.5 cursor-pointer hover:bg-gray-50 transition-colors ${groupItems.length > 1 ? '' : ''}`}
              onClick={() => groupItems.length > 1 ? toggleGroup(groupKey) : onRowClick(first)}>
              {groupItems.length > 1 && (
                <button className="text-xs text-gray-400 w-4 shrink-0" onClick={(e) => { e.stopPropagation(); toggleGroup(groupKey); }}>
                  {isExpanded ? "▼" : "▶"}
                </button>
              )}
              {groupItems.length === 1 && <span className="w-4 shrink-0" />}

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
              <StatusBadge value={first.fact_level || "REVIEW_NEEDED"} />
            </div>

            {/* 펼쳐진 atomic 항목들 */}
            {isExpanded && groupItems.length > 1 && (
              <div className="bg-gray-50/50">
                {groupItems.map((r: any, i: number) => (
                  <div key={r.id} className="flex items-center gap-3 px-4 py-2 pl-12 border-t border-gray-100 cursor-pointer hover:bg-blue-50/50 transition-colors"
                    onClick={() => onRowClick(r)}>
                    <span className="font-mono text-[10px] text-gray-500 w-16 shrink-0">{r.requirement_code}</span>
                    <span className="text-xs text-gray-700 truncate flex-1">{r.title}</span>
                    <span className="text-[10px] text-gray-400 shrink-0">{CATEGORY_LABELS_SHORT[r.category] || r.category}</span>
                    {r.mandatory_flag && <span className="text-[10px] bg-red-100 text-red-700 px-1 py-0.5 rounded shrink-0">필수</span>}
                    {r.query_needed && <span className="text-[10px] bg-orange-100 text-orange-700 px-1 py-0.5 rounded shrink-0">질의</span>}
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

function StatCard({ label, count, color, active, onClick }: {
  label: string; count: number; color: string; active?: boolean; onClick?: () => void;
}) {
  return (
    <button onClick={onClick}
      className={`rounded-lg px-3 py-2 text-center transition-all ${color} ${
        active ? "ring-2 ring-blue-400 shadow-sm" : "hover:shadow-sm"
      }`}>
      <div className="text-lg font-bold">{count}</div>
      <div className="text-[11px]">{label}</div>
    </button>
  );
}
