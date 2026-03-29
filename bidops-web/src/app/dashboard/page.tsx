"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { projectApi, documentApi, requirementApi, analysisJobApi, checklistApi, inquiryApi } from "@/lib/api";
import StatusBadge from "@/components/common/StatusBadge";
import { useAuth } from "@/lib/auth";

interface ProjectSummary {
  id: string;
  name: string;
  client_name: string;
  business_name: string;
  status: string;
  created_at: number[];
  docCount: number;
  reqCount: number;
  jobCount: number;
  checklistCount: number;
  inquiryCount: number;
}

export default function DashboardPage() {
  const { user } = useAuth();
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [totals, setTotals] = useState({ projects: 0, docs: 0, reqs: 0, jobs: 0, checklists: 0, inquiries: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = () => {
    setError("");
    setLoading(true);
    projectApi.list().then(async (data) => {
      const items = data.items || [];

      const summaries: ProjectSummary[] = await Promise.all(
        items.map(async (p: any) => {
          const [docs, reqs, jobs, cls, inqs] = await Promise.all([
            documentApi.list(p.id).then(d => d.items?.length || 0).catch(() => 0),
            requirementApi.list(p.id).then(d => d.total_count || 0).catch(() => 0),
            analysisJobApi.list(p.id).then(d => d.items?.length || 0).catch(() => 0),
            checklistApi.list(p.id).then(d => d?.length || 0).catch(() => 0),
            inquiryApi.list(p.id).then(d => d?.length || 0).catch(() => 0),
          ]);
          return { ...p, docCount: docs, reqCount: reqs, jobCount: jobs, checklistCount: cls, inquiryCount: inqs };
        })
      );

      setProjects(summaries);
      setTotals({
        projects: summaries.length,
        docs: summaries.reduce((s, p) => s + p.docCount, 0),
        reqs: summaries.reduce((s, p) => s + p.reqCount, 0),
        jobs: summaries.reduce((s, p) => s + p.jobCount, 0),
        checklists: summaries.reduce((s, p) => s + p.checklistCount, 0),
        inquiries: summaries.reduce((s, p) => s + p.inquiryCount, 0),
      });
    }).catch((e) => {
      setError(e.code === "UNAUTHORIZED" ? "로그인이 필요합니다." : (e.message || "데이터를 불러올 수 없습니다."));
    }).finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const formatDate = (arr: number[]) =>
    arr ? arr[0] + "." + String(arr[1]).padStart(2, "0") + "." + String(arr[2]).padStart(2, "0") : "-";

  const STAT_CARDS = [
    { label: "프로젝트", value: totals.projects, color: "bg-blue-50 text-blue-700", href: "/projects" },
    { label: "문서", value: totals.docs, color: "bg-green-50 text-green-700" },
    { label: "요구사항", value: totals.reqs, color: "bg-purple-50 text-purple-700" },
    { label: "분석 Job", value: totals.jobs, color: "bg-yellow-50 text-yellow-700" },
    { label: "체크리스트", value: totals.checklists, color: "bg-orange-50 text-orange-700" },
    { label: "질의", value: totals.inquiries, color: "bg-red-50 text-red-700" },
  ];

  return (
    <div>
      {/* Header with greeting */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold">대시보드</h1>
          {user && <p className="text-sm text-gray-500 mt-0.5">{user.name}님, 환영합니다.</p>}
        </div>
        <Link href="/projects" className="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700 transition-colors">
          프로젝트 관리
        </Link>
      </div>

      {/* Error */}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded mb-4 flex items-center justify-between" role="alert">
          <span>{error}</span>
          <button onClick={load} className="text-xs text-red-600 hover:underline font-medium ml-3">다시 시도</button>
        </div>
      )}

      {/* Stats cards */}
      <div className="grid grid-cols-6 gap-4 mb-6">
        {STAT_CARDS.map((c) => {
          const content = (
            <div className={`rounded-lg border p-4 text-center transition-all ${c.color} ${c.href ? "hover:shadow-md cursor-pointer" : ""}`}>
              <div className="text-2xl font-bold">{loading ? "-" : c.value}</div>
              <div className="text-xs mt-1 opacity-70">{c.label}</div>
            </div>
          );
          return c.href
            ? <Link key={c.label} href={c.href}>{content}</Link>
            : <div key={c.label}>{content}</div>;
        })}
      </div>

      {/* Loading */}
      {loading && (
        <div className="bg-white rounded-lg border p-12 text-center text-gray-400 text-sm">
          프로젝트 데이터를 불러오는 중...
        </div>
      )}

      {/* Empty state - no projects */}
      {!loading && !error && projects.length === 0 && (
        <div className="bg-white rounded-lg border p-12 text-center">
          <div className="text-gray-400 text-sm mb-4">아직 참여 중인 프로젝트가 없습니다.</div>
          <div className="space-y-2 text-sm text-gray-600">
            <p>1. 프로젝트를 생성하고 RFP PDF를 업로드하세요.</p>
            <p>2. AI가 요구사항을 자동 추출합니다.</p>
            <p>3. 검토하고 누락 항목을 확인하세요.</p>
          </div>
          <Link href="/projects"
            className="inline-block mt-6 px-6 py-2.5 bg-blue-600 text-white rounded text-sm hover:bg-blue-700 transition-colors">
            첫 프로젝트 만들기
          </Link>
        </div>
      )}

      {/* Project summary table */}
      {!loading && projects.length > 0 && (
        <div className="bg-white rounded-lg border">
          <div className="px-4 py-3 border-b flex justify-between items-center">
            <h2 className="font-semibold text-sm">프로젝트 현황</h2>
            <Link href="/projects" className="text-xs text-blue-600 hover:underline">전체 보기</Link>
          </div>
          <table className="w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="text-left px-4 py-2">프로젝트</th>
                <th className="text-left px-4 py-2">발주처</th>
                <th className="text-left px-4 py-2">상태</th>
                <th className="text-center px-4 py-2">문서</th>
                <th className="text-center px-4 py-2">요구사항</th>
                <th className="text-center px-4 py-2">체크리스트</th>
                <th className="text-center px-4 py-2">질의</th>
                <th className="text-left px-4 py-2">생성일</th>
              </tr>
            </thead>
            <tbody>
              {projects.map((p) => (
                <tr key={p.id} className="border-t hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-2.5">
                    <Link href={"/projects/" + p.id} className="text-blue-600 hover:underline font-medium">{p.name}</Link>
                    {p.business_name && <div className="text-[11px] text-gray-400 mt-0.5 truncate max-w-[200px]">{p.business_name}</div>}
                  </td>
                  <td className="px-4 py-2.5 text-gray-500">{p.client_name}</td>
                  <td className="px-4 py-2.5"><StatusBadge value={p.status || "DRAFT"} /></td>
                  <td className="px-4 py-2.5 text-center">{p.docCount}</td>
                  <td className="px-4 py-2.5 text-center font-medium">{p.reqCount}</td>
                  <td className="px-4 py-2.5 text-center">{p.checklistCount}</td>
                  <td className="px-4 py-2.5 text-center">{p.inquiryCount}</td>
                  <td className="px-4 py-2.5 text-gray-400 text-xs">{formatDate(p.created_at)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
