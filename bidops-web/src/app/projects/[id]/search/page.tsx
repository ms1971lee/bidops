"use client";

import { useParams, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useEffect, useState, useCallback } from "react";
import { requirementApi } from "@/lib/api";
import StatusBadge from "@/components/common/StatusBadge";

const CATEGORY_LABELS: Record<string, string> = {
  BUSINESS_OVERVIEW: "사업개요", BACKGROUND: "배경", OBJECTIVE: "목표", SCOPE: "범위",
  FUNCTIONAL: "기능", NON_FUNCTIONAL: "비기능", PERFORMANCE: "성능", SECURITY: "보안",
  QUALITY: "품질", TESTING: "시험", DATA_INTEGRATION: "데이터/연계", UI_UX: "UI/UX",
  INFRASTRUCTURE: "인프라", PERSONNEL: "인력", TRACK_RECORD: "실적", SCHEDULE: "일정",
  DELIVERABLE: "산출물", SUBMISSION: "제출", PROPOSAL_GUIDE: "제안안내", EVALUATION: "평가",
  PRESENTATION: "발표", MAINTENANCE: "유지보수", TRAINING: "교육", LEGAL: "법률", ETC: "기타",
};

const SEARCH_TABS = [
  { key: "requirement", label: "유사 요구사항" },
  { key: "deliverable", label: "산출물 구조" },
  { key: "track_record", label: "실적 문구" },
];

type SearchTab = "requirement" | "deliverable" | "track_record";

const TAB_CATEGORIES: Record<SearchTab, string[]> = {
  requirement: [],
  deliverable: ["DELIVERABLE", "SUBMISSION"],
  track_record: ["TRACK_RECORD", "PERSONNEL"],
};

export default function SearchPage() {
  const { id } = useParams() as { id: string };
  const searchParams = useSearchParams();

  const initialKeyword = searchParams.get("keyword") || "";
  const initialCategory = searchParams.get("category") || "";

  const [tab, setTab] = useState<SearchTab>("requirement");
  const [keyword, setKeyword] = useState(initialKeyword);
  const [searchInput, setSearchInput] = useState(initialKeyword);
  const [categoryFilter, setCategoryFilter] = useState(initialCategory);
  const [results, setResults] = useState<any[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [analysisCache, setAnalysisCache] = useState<Record<string, any>>({});

  const doSearch = useCallback(() => {
    if (!keyword.trim() && !categoryFilter) return;
    setLoading(true);
    setSearched(true);

    const p = new URLSearchParams();
    if (keyword.trim()) p.set("keyword", keyword.trim());

    const tabCategories = TAB_CATEGORIES[tab];
    if (categoryFilter) {
      p.set("category", categoryFilter);
    } else if (tabCategories.length > 0) {
      p.set("category", tabCategories[0]);
    }

    p.set("size", "50");

    requirementApi.list(id, p.toString())
      .then((d) => {
        let items = d.items || [];
        if (tabCategories.length > 1 && !categoryFilter) {
          // search other categories in tab too
          const otherSearches = tabCategories.slice(1).map((cat) => {
            const p2 = new URLSearchParams();
            if (keyword.trim()) p2.set("keyword", keyword.trim());
            p2.set("category", cat);
            p2.set("size", "50");
            return requirementApi.list(id, p2.toString()).catch(() => ({ items: [] }));
          });
          Promise.all(otherSearches).then((others) => {
            const all = [...items];
            others.forEach((o: any) => { if (o.items) all.push(...o.items); });
            const unique = Array.from(new Map(all.map((i) => [i.id, i])).values());
            setResults(unique);
            setTotalCount(unique.length);
            setLoading(false);
          });
        } else {
          setResults(items);
          setTotalCount(d.total_count || items.length);
          setLoading(false);
        }
      })
      .catch(() => { setResults([]); setTotalCount(0); setLoading(false); });
  }, [id, keyword, categoryFilter, tab]);

  // auto-search if URL params present
  useEffect(() => {
    if (initialKeyword || initialCategory) {
      doSearch();
    }
  }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setKeyword(searchInput);
  };

  // trigger search on keyword/tab change
  useEffect(() => {
    if (keyword.trim() || categoryFilter) doSearch();
  }, [keyword, tab]);

  const toggleExpand = async (reqId: string) => {
    if (expandedId === reqId) {
      setExpandedId(null);
      return;
    }
    setExpandedId(reqId);
    if (!analysisCache[reqId]) {
      try {
        const analysis = await requirementApi.getAnalysis(id, reqId);
        setAnalysisCache((prev) => ({ ...prev, [reqId]: analysis }));
      } catch {
        setAnalysisCache((prev) => ({ ...prev, [reqId]: null }));
      }
    }
  };

  return (
    <div>
      {/* 안내 배너 */}
      <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 mb-4 text-sm text-amber-800">
        <span className="font-medium">참고용 검색 도구</span>
        <span className="text-amber-600 ml-2">
          — 검색 결과는 현재 프로젝트의 요구사항을 기반으로 합니다. 검토 후 제안서 작성에 활용하세요.
        </span>
      </div>

      {/* 검색 바 */}
      <div className="bg-white rounded border p-4 mb-4">
        <form onSubmit={handleSearch} className="flex gap-2 items-end">
          <div className="flex-1">
            <label className="block text-xs text-gray-500 mb-1">키워드 검색</label>
            <input value={searchInput} onChange={(e) => setSearchInput(e.target.value)}
              className="w-full border rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="검색어를 입력하세요 (예: 보안, 성능, API 연계...)" />
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">카테고리</label>
            <select value={categoryFilter} onChange={(e) => setCategoryFilter(e.target.value)}
              className="border rounded px-3 py-2 text-sm">
              <option value="">전체 카테고리</option>
              {Object.entries(CATEGORY_LABELS).map(([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ))}
            </select>
          </div>
          <button type="submit"
            className="px-6 py-2 bg-blue-600 text-white rounded text-sm font-medium hover:bg-blue-700">
            검색
          </button>
        </form>
      </div>

      {/* 검색 유형 탭 */}
      <nav className="flex gap-1 border-b mb-4">
        {SEARCH_TABS.map((t) => (
          <button key={t.key} onClick={() => setTab(t.key as SearchTab)}
            className={`px-4 py-2 text-sm border-b-2 transition-colors ${
              tab === t.key ? "border-blue-600 text-blue-600 font-medium" : "border-transparent text-gray-500 hover:text-gray-700"
            }`}>
            {t.label}
          </button>
        ))}
      </nav>

      {/* 결과 */}
      {!searched ? (
        <div className="text-center text-gray-400 py-16">
          <div className="text-lg mb-2">키워드 또는 카테고리를 선택하여 검색하세요</div>
          <div className="text-sm">유사한 요구사항, 산출물 구조, 실적 문구를 찾을 수 있습니다</div>
        </div>
      ) : loading ? (
        <div className="text-center text-gray-400 py-12">검색 중...</div>
      ) : (
        <div>
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs text-gray-500">검색 결과 {totalCount}건</span>
            {keyword && (
              <span className="text-xs text-gray-400">
                &quot;{keyword}&quot; {categoryFilter ? `/ ${CATEGORY_LABELS[categoryFilter] || categoryFilter}` : ""}
              </span>
            )}
          </div>

          {results.length === 0 ? (
            <div className="text-center text-gray-400 py-12 bg-white rounded border">
              검색 결과가 없습니다. 다른 키워드로 시도해보세요.
            </div>
          ) : (
            <div className="space-y-2">
              {results.map((r) => (
                <ResultCard key={r.id} item={r} projectId={id}
                  expanded={expandedId === r.id}
                  analysis={analysisCache[r.id]}
                  onToggle={() => toggleExpand(r.id)} />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ── Result Card ─────────────────────────────────────────────────────
function ResultCard({ item, projectId, expanded, analysis, onToggle }: {
  item: any; projectId: string; expanded: boolean; analysis: any; onToggle: () => void;
}) {
  return (
    <div className="bg-white rounded border overflow-hidden">
      {/* 요약 행 */}
      <div className="flex items-start gap-3 px-4 py-3 cursor-pointer hover:bg-gray-50 transition-colors"
        onClick={onToggle}>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className="font-mono text-xs text-gray-500">{item.requirement_code}</span>
            <StatusBadge value={item.category} />
            <StatusBadge value={item.fact_level || "REVIEW_NEEDED"} />
            {item.mandatory_flag && <span className="text-xs text-red-500 font-bold">필수</span>}
          </div>
          <div className="text-sm font-medium">{item.title}</div>
          {item.original_text && (
            <div className="text-xs text-gray-500 mt-1 line-clamp-2">{item.original_text}</div>
          )}
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <Link href={`/projects/${projectId}/requirements/${item.id}`}
            className="text-xs text-blue-600 hover:underline"
            onClick={(e) => e.stopPropagation()}>
            상세
          </Link>
          <span className="text-gray-400 text-xs">{expanded ? "▲" : "▼"}</span>
        </div>
      </div>

      {/* 확장 영역 */}
      {expanded && (
        <div className="border-t px-4 py-3 bg-gray-50/50 space-y-3">
          <div className="bg-amber-50 border border-amber-100 rounded px-3 py-1.5 text-xs text-amber-700">
            아래 내용은 AI 분석 참고 자료입니다. 검토 후 활용하세요.
          </div>

          {/* 원문 */}
          {item.original_text && (
            <Field label="원문">
              <div className="bg-white p-2.5 rounded border text-xs whitespace-pre-wrap leading-relaxed">
                {item.original_text}
              </div>
            </Field>
          )}

          {/* AI 분석 결과 (별도 API 로드) */}
          {analysis === undefined && (
            <div className="text-xs text-gray-400">분석 정보 로딩 중...</div>
          )}

          {analysis && (
            <>
              {analysis.fact_summary && (
                <Field label="확정 근거" badge="원문 기반">
                  <div className="bg-green-50 p-2.5 rounded text-xs whitespace-pre-wrap">
                    {analysis.fact_summary}
                  </div>
                </Field>
              )}

              {analysis.interpretation_summary && (
                <Field label="AI 추론" badge="참고용">
                  <div className="bg-yellow-50 p-2.5 rounded text-xs whitespace-pre-wrap">
                    {analysis.interpretation_summary}
                  </div>
                </Field>
              )}

              {analysis.proposal_point && (
                <Field label="제안 포인트">
                  <div className="bg-blue-50 p-2.5 rounded text-xs whitespace-pre-wrap">
                    {analysis.proposal_point}
                  </div>
                </Field>
              )}

              {analysis.implementation_approach && (
                <Field label="구현 방향">
                  <div className="bg-white p-2.5 rounded border text-xs whitespace-pre-wrap">
                    {analysis.implementation_approach}
                  </div>
                </Field>
              )}

              {analysis.expected_deliverables?.length > 0 && (
                <Field label="필요 산출물">
                  <ul className="list-disc list-inside bg-white p-2.5 rounded border text-xs space-y-0.5">
                    {analysis.expected_deliverables.map((d: string, i: number) => (
                      <li key={i}>{d}</li>
                    ))}
                  </ul>
                </Field>
              )}

              {analysis.differentiation_point && (
                <Field label="차별화 포인트">
                  <div className="bg-purple-50 p-2.5 rounded text-xs whitespace-pre-wrap">
                    {analysis.differentiation_point}
                  </div>
                </Field>
              )}

              {analysis.risk_note?.length > 0 && (
                <Field label="리스크/주의사항">
                  <ul className="list-disc list-inside bg-red-50 p-2.5 rounded text-xs space-y-0.5">
                    {analysis.risk_note.map((r: string, i: number) => (
                      <li key={i}>{r}</li>
                    ))}
                  </ul>
                </Field>
              )}
            </>
          )}

          {analysis === null && (
            <div className="text-xs text-gray-400">AI 분석 결과가 없습니다.</div>
          )}

          {/* 출처 정보 */}
          <div className="flex items-center gap-3 pt-2 border-t text-xs text-gray-500">
            <span>카테고리: {CATEGORY_LABELS[item.category] || item.category}</span>
            <span>근거수준: <StatusBadge value={item.fact_level || "REVIEW_NEEDED"} /></span>
            <span>검토: <StatusBadge value={item.review_status || "NOT_REVIEWED"} /></span>
            <Link href={`/projects/${projectId}/requirements/${item.id}`}
              className="ml-auto text-blue-600 hover:underline">
              요구사항 상세 보기 &rarr;
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}

function Field({ label, badge, children }: { label: string; badge?: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="flex items-center gap-1.5 mb-1">
        <span className="text-xs text-gray-500 font-medium">{label}</span>
        {badge && (
          <span className="text-[10px] bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded">{badge}</span>
        )}
      </div>
      {children}
    </div>
  );
}
