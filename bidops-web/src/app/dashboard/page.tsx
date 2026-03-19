"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { projectApi, documentApi, requirementApi, analysisJobApi, checklistApi, inquiryApi } from "@/lib/api";
import StatusBadge from "@/components/common/StatusBadge";

interface ProjectSummary {
  id: string;
  name: string;
  client_name: string;
  status: string;
  created_at: number[];
  docCount: number;
  reqCount: number;
  jobCount: number;
  checklistCount: number;
  inquiryCount: number;
}

export default function DashboardPage() {
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [totals, setTotals] = useState({ projects: 0, docs: 0, reqs: 0, jobs: 0, checklists: 0, inquiries: 0 });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
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
      setLoading(false);
    }).catch(() => setLoading(false));
  }, []);

  const formatDate = (arr: number[]) =>
    arr ? arr[0] + "-" + String(arr[1]).padStart(2, "0") + "-" + String(arr[2]).padStart(2, "0") : "-";

  const STAT_CARDS = [
    { label: "프로젝트", value: totals.projects, color: "bg-blue-50 text-blue-700" },
    { label: "문서", value: totals.docs, color: "bg-green-50 text-green-700" },
    { label: "요구사항", value: totals.reqs, color: "bg-purple-50 text-purple-700" },
    { label: "분석 Job", value: totals.jobs, color: "bg-yellow-50 text-yellow-700" },
    { label: "체크리스트", value: totals.checklists, color: "bg-orange-50 text-orange-700" },
    { label: "질의", value: totals.inquiries, color: "bg-red-50 text-red-700" },
  ];

  return (
    <div>
      <h1 className="text-xl font-bold mb-6">대시보드</h1>

      {/* 전체 통계 카드 */}
      <div className="grid grid-cols-6 gap-4 mb-8">
        {STAT_CARDS.map((c) => (
          <div key={c.label} className={"rounded border p-4 text-center " + c.color}>
            <div className="text-2xl font-bold">{loading ? "-" : c.value}</div>
            <div className="text-xs mt-1 opacity-70">{c.label}</div>
          </div>
        ))}
      </div>

      {/* 프로젝트별 요약 테이블 */}
      <div className="bg-white rounded border">
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
              <th className="text-center px-4 py-2">Job</th>
              <th className="text-center px-4 py-2">체크리스트</th>
              <th className="text-center px-4 py-2">질의</th>
              <th className="text-left px-4 py-2">생성일</th>
            </tr>
          </thead>
          <tbody>
            {projects.map((p) => (
              <tr key={p.id} className="border-t hover:bg-gray-50">
                <td className="px-4 py-2">
                  <Link href={"/projects/" + p.id} className="text-blue-600 hover:underline font-medium">{p.name}</Link>
                </td>
                <td className="px-4 py-2 text-gray-500">{p.client_name}</td>
                <td className="px-4 py-2"><StatusBadge value={p.status} /></td>
                <td className="px-4 py-2 text-center">{p.docCount}</td>
                <td className="px-4 py-2 text-center font-medium">{p.reqCount}</td>
                <td className="px-4 py-2 text-center">{p.jobCount}</td>
                <td className="px-4 py-2 text-center">{p.checklistCount}</td>
                <td className="px-4 py-2 text-center">{p.inquiryCount}</td>
                <td className="px-4 py-2 text-gray-400 text-xs">{formatDate(p.created_at)}</td>
              </tr>
            ))}
            {!loading && projects.length === 0 && (
              <tr><td colSpan={9} className="px-4 py-12 text-center text-gray-400">
                프로젝트가 없습니다. <Link href="/projects" className="text-blue-600 hover:underline">새 프로젝트 만들기</Link>
              </td></tr>
            )}
            {loading && (
              <tr><td colSpan={9} className="px-4 py-12 text-center text-gray-400">로딩 중...</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
