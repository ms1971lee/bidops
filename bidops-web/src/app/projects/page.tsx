"use client";

import { useEffect, useState, useMemo } from "react";
import Link from "next/link";
import { projectApi } from "@/lib/api";
import StatusBadge from "@/components/common/StatusBadge";

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
    <div>
      {/* Header */}
      <div className="flex items-center justify-between mb-5">
        <div>
          <h1 className="text-xl font-bold">프로젝트</h1>
          <p className="text-sm text-gray-500 mt-0.5">{projects.length}개 프로젝트</p>
        </div>
        <button onClick={() => setShowForm(!showForm)}
          className="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700 transition-colors">
          {showForm ? "취소" : "+ 새 프로젝트"}
        </button>
      </div>

      {/* Create form */}
      {showForm && (
        <div className="bg-white rounded-lg border p-4 mb-4">
          <h3 className="text-sm font-medium mb-3">새 프로젝트 생성</h3>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="block text-xs text-gray-500 mb-1">프로젝트명 *</label>
              <input className="w-full border px-3 py-1.5 rounded text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
                value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="2026 XX시스템 구축" />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">발주처</label>
              <input className="w-full border px-3 py-1.5 rounded text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
                value={form.client_name} onChange={(e) => setForm({ ...form, client_name: e.target.value })}
                placeholder="XX공사" />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">사업명</label>
              <input className="w-full border px-3 py-1.5 rounded text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
                value={form.business_name} onChange={(e) => setForm({ ...form, business_name: e.target.value })}
                placeholder="통합정보시스템 구축 사업" />
            </div>
          </div>
          <div className="flex justify-end mt-3">
            <button onClick={handleCreate} disabled={creating || !form.name.trim()}
              className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm disabled:opacity-50 hover:bg-blue-700 transition-colors">
              {creating ? "생성 중..." : "생성"}
            </button>
          </div>
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded mb-4 flex items-center justify-between" role="alert">
          <span>{error}</span>
          <button onClick={load} className="text-xs text-red-600 hover:underline font-medium ml-3">다시 시도</button>
        </div>
      )}

      {/* Filters */}
      {!loading && projects.length > 0 && (
        <div className="flex items-center gap-2 mb-4">
          <input value={keyword} onChange={(e) => setKeyword(e.target.value)}
            placeholder="프로젝트 검색..."
            className="border rounded px-3 py-1.5 text-sm w-56 focus:ring-2 focus:ring-blue-500 focus:outline-none" />
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}
            className="border rounded px-2 py-1.5 text-sm" aria-label="상태 필터">
            {STATUS_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
          {hasFilter && (
            <button onClick={() => { setKeyword(""); setStatusFilter(""); }}
              className="text-xs text-gray-400 hover:text-red-500">초기화</button>
          )}
          <span className="text-xs text-gray-400 ml-auto">{filtered.length}건</span>
        </div>
      )}

      {/* Loading */}
      {loading && (
        <div className="text-center text-gray-400 py-16 text-sm">프로젝트 목록을 불러오는 중...</div>
      )}

      {/* Empty */}
      {!loading && !error && projects.length === 0 && (
        <div className="bg-white rounded-lg border text-center py-16">
          <div className="text-gray-400 text-sm mb-3">프로젝트가 없습니다</div>
          <button onClick={() => setShowForm(true)}
            className="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700">
            첫 프로젝트 만들기
          </button>
        </div>
      )}

      {/* Filter empty */}
      {!loading && projects.length > 0 && filtered.length === 0 && hasFilter && (
        <div className="bg-white rounded-lg border text-center py-12 text-sm text-gray-400">
          검색 조건에 맞는 프로젝트가 없습니다
        </div>
      )}

      {/* Project cards */}
      {!loading && filtered.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {filtered.map((p) => (
            <Link key={p.id} href={`/projects/${p.id}`}
              className="bg-white rounded-lg border p-4 hover:shadow-md hover:border-blue-300 transition-all group">
              <div className="flex items-start justify-between mb-2">
                <div className="min-w-0 flex-1">
                  <h3 className="font-medium text-sm group-hover:text-blue-600 truncate">{p.name}</h3>
                  <div className="text-xs text-gray-500 mt-0.5 truncate">
                    {p.client_name}{p.business_name ? ` - ${p.business_name}` : ""}
                  </div>
                </div>
                <StatusBadge value={p.status || "DRAFT"} />
              </div>

              <div className="flex items-center gap-3 text-xs text-gray-400 mt-3 pt-3 border-t">
                <span>{formatDate(p.created_at)}</span>
                {p.bid_type && <span className="bg-gray-100 px-1.5 py-0.5 rounded text-gray-600">{p.bid_type}</span>}
                {p.due_date && (
                  <span className="ml-auto text-orange-500">
                    마감: {typeof p.due_date === "string" ? p.due_date : formatDate(p.due_date)}
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
