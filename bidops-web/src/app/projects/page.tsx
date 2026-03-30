"use client";

import { useEffect, useState, useMemo } from "react";
import Link from "next/link";
import { projectApi } from "@/lib/api";
import StatusBadge from "@/components/common/StatusBadge";
import EmptyState from "@/components/common/EmptyState";
import LoadingState from "@/components/common/LoadingState";
import ErrorState from "@/components/common/ErrorState";
import AppCard from "@/components/common/AppCard";
import AppButton from "@/components/common/AppButton";

const STATUS_OPTIONS = [
  { value: "", label: "전체 상태" },
  { value: "DRAFT", label: "초안" },
  { value: "ANALYZING", label: "분석중" },
  { value: "REVIEWING", label: "검토중" },
  { value: "SUBMISSION_PREP", label: "제출준비" },
  { value: "COMPLETED", label: "완료" },
  { value: "CLOSED", label: "종료" },
];

export default function ProjectsPage() {
  const [projects, setProjects] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // filters
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState("");

  // create form
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ name: "", client_name: "", business_name: "" });
  const [creating, setCreating] = useState(false);

  const load = () => {
    setLoading(true);
    setError("");
    projectApi.list()
      .then((d) => setProjects(d.items || []))
      .catch((e) => setError(e.code === "UNAUTHORIZED" ? "로그인이 필요합니다." : (e.message || "프로젝트 목록을 불러올 수 없습니다.")))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const filtered = useMemo(() => {
    let list = projects;
    if (statusFilter) list = list.filter((p) => p.status === statusFilter);
    if (keyword.trim()) {
      const kw = keyword.trim().toLowerCase();
      list = list.filter((p) =>
        (p.name || "").toLowerCase().includes(kw) ||
        (p.client_name || "").toLowerCase().includes(kw) ||
        (p.business_name || "").toLowerCase().includes(kw)
      );
    }
    return list;
  }, [projects, statusFilter, keyword]);

  const handleCreate = async () => {
    if (!form.name.trim()) return;
    setCreating(true);
    try {
      await projectApi.create(form);
      setForm({ name: "", client_name: "", business_name: "" });
      setShowForm(false);
      load();
    } catch (e: any) {
      alert(e.message || "프로젝트 생성 실패");
    } finally {
      setCreating(false);
    }
  };

  const formatDate = (arr: number[]) => {
    if (!arr || arr.length < 3) return "-";
    return `${arr[0]}.${String(arr[1]).padStart(2, "0")}.${String(arr[2]).padStart(2, "0")}`;
  };

  const hasFilter = !!(keyword || statusFilter);

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-bold text-gray-800">프로젝트</h1>
          <p className="text-[11px] text-gray-400 mt-0.5">{projects.length}개 프로젝트</p>
        </div>
        <AppButton onClick={() => setShowForm(!showForm)}>
          {showForm ? "취소" : "+ 새 프로젝트"}
        </AppButton>
      </div>

      {/* Create form */}
      {showForm && (
        <AppCard padding="lg">
          <h3 className="text-xs font-semibold text-gray-600 mb-3">새 프로젝트 생성</h3>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="block text-[11px] text-gray-400 mb-1">프로젝트명 *</label>
              <input className="w-full border border-gray-200 px-3 py-2 rounded-lg text-sm focus:ring-2 focus:ring-indigo-300 focus:border-indigo-300 focus:outline-none transition-colors"
                value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="2026 XX시스템 구축" />
            </div>
            <div>
              <label className="block text-[11px] text-gray-400 mb-1">발주처</label>
              <input className="w-full border border-gray-200 px-3 py-2 rounded-lg text-sm focus:ring-2 focus:ring-indigo-300 focus:border-indigo-300 focus:outline-none transition-colors"
                value={form.client_name} onChange={(e) => setForm({ ...form, client_name: e.target.value })}
                placeholder="XX공사" />
            </div>
            <div>
              <label className="block text-[11px] text-gray-400 mb-1">사업명</label>
              <input className="w-full border border-gray-200 px-3 py-2 rounded-lg text-sm focus:ring-2 focus:ring-indigo-300 focus:border-indigo-300 focus:outline-none transition-colors"
                value={form.business_name} onChange={(e) => setForm({ ...form, business_name: e.target.value })}
                placeholder="통합정보시스템 구축 사업" />
            </div>
          </div>
          <div className="flex justify-end mt-4">
            <AppButton onClick={handleCreate} disabled={creating || !form.name.trim()}>
              {creating ? "생성 중..." : "생성"}
            </AppButton>
          </div>
        </AppCard>
      )}

      {/* Error */}
      {error && <ErrorState title={error} compact onRetry={load} />}

      {/* Filters */}
      {!loading && projects.length > 0 && (
        <div className="flex items-center gap-2">
          <input value={keyword} onChange={(e) => setKeyword(e.target.value)}
            placeholder="프로젝트 검색..."
            className="border border-gray-200 rounded-lg px-3 py-1.5 text-sm w-56 focus:ring-2 focus:ring-indigo-300 focus:border-indigo-300 focus:outline-none transition-colors" />
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}
            className="border border-gray-200 rounded-lg px-2 py-1.5 text-sm" aria-label="상태 필터">
            {STATUS_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
          {hasFilter && (
            <button onClick={() => { setKeyword(""); setStatusFilter(""); }}
              className="text-[11px] text-gray-400 hover:text-indigo-600 transition-colors">초기화</button>
          )}
          <span className="text-[11px] text-gray-300 ml-auto tabular-nums">{filtered.length}건</span>
        </div>
      )}

      {loading && <LoadingState variant="list" rows={3} />}

      {!loading && !error && projects.length === 0 && (
        <EmptyState title="프로젝트가 없습니다" description="새 프로젝트를 만들어 RFP 분석을 시작하세요."
          primaryAction={{ label: "첫 프로젝트 만들기", onClick: () => setShowForm(true) }} />
      )}

      {!loading && projects.length > 0 && filtered.length === 0 && hasFilter && (
        <EmptyState title="조건에 맞는 결과가 없습니다" description="필터를 조정하거나 검색어를 변경해보세요." compact
          secondaryAction={{ label: "초기화", onClick: () => { setKeyword(""); setStatusFilter(""); } }} />
      )}

      {/* Project cards */}
      {!loading && filtered.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
          {filtered.map((p) => (
            <Link key={p.id} href={`/projects/${p.id}`}
              className="block bg-white rounded-xl border border-gray-100 shadow-sm p-4 hover:shadow-md hover:border-indigo-200 transition-all group">
              <div className="flex items-start justify-between mb-2">
                <div className="min-w-0 flex-1">
                  <h3 className="font-medium text-sm text-gray-800 group-hover:text-indigo-600 truncate transition-colors">{p.name}</h3>
                  <div className="text-[11px] text-gray-400 mt-0.5 truncate">
                    {p.client_name}{p.business_name ? ` · ${p.business_name}` : ""}
                  </div>
                </div>
                <StatusBadge value={p.status || "DRAFT"} />
              </div>

              <div className="flex items-center gap-3 text-[11px] text-gray-300 mt-3 pt-3 border-t border-gray-50">
                <span>{formatDate(p.created_at)}</span>
                {p.bid_type && <span className="bg-gray-50 px-1.5 py-0.5 rounded-md text-gray-500 border border-gray-100">{p.bid_type}</span>}
                {p.due_date && (
                  <span className="ml-auto text-amber-500">
                    마감 {typeof p.due_date === "string" ? p.due_date : formatDate(p.due_date)}
                  </span>
                )}
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
