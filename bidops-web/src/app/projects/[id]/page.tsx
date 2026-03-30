"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import { useEffect, useState } from "react";
import { documentApi, analysisJobApi, requirementApi, checklistApi, inquiryApi } from "@/lib/api";
import LoadingState from "@/components/common/LoadingState";
import ErrorState from "@/components/common/ErrorState";
import EmptyState from "@/components/common/EmptyState";
import AppCard from "@/components/common/AppCard";
import SectionHeader from "@/components/common/SectionHeader";
import AppButton from "@/components/common/AppButton";
import { useProjectRole } from "@/lib/useProjectRole";
import StatusBadge from "@/components/common/StatusBadge";

interface Stats {
  docs: number;
  docsParsing: number;
  docsFailed: number;
  totalReqs: number;
  notReviewed: number;
  inReview: number;
  approved: number;
  hold: number;
  needsUpdate: number;
  queryNeeded: number;
  mandatoryNotDone: number;
  highRiskItems: number;
  totalChecklistItems: number;
  doneChecklistItems: number;
  inquiries: number;
}

const EMPTY: Stats = {
  docs: 0, docsParsing: 0, docsFailed: 0,
  totalReqs: 0, notReviewed: 0, inReview: 0, approved: 0, hold: 0, needsUpdate: 0,
  queryNeeded: 0, mandatoryNotDone: 0, highRiskItems: 0, totalChecklistItems: 0, doneChecklistItems: 0, inquiries: 0,
};

export default function AnalysisDashboard() {
  const { id } = useParams() as { id: string };
  const { role, isOwner, loading: roleLoading } = useProjectRole(id);

  const [stats, setStats] = useState<Stats>(EMPTY);
  const [jobs, setJobs] = useState<any[]>([]);
  const [recentReviewed, setRecentReviewed] = useState<any[]>([]);
  const [queryItems, setQueryItems] = useState<any[]>([]);
  const [highRiskList, setHighRiskList] = useState<any[]>([]);
  const [needsReviewItems, setNeedsReviewItems] = useState<any[]>([]);
  const [previewTab, setPreviewTab] = useState<"review" | "checklist" | "query">("review");
  const [qualityStats, setQualityStats] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadData = () => {
    setLoading(true);
    setError("");
    Promise.all([
      // docs
      documentApi.list(id).then((d) => d.items || []).catch(() => []),
      // all requirements (use large page to get counts)
      requirementApi.list(id, "size=500").catch(() => ({ items: [], total_count: 0 })),
      // checklists with items
      checklistApi.list(id).catch(() => []),
      // inquiries
      inquiryApi.list(id).catch(() => []),
      // analysis jobs
      analysisJobApi.list(id).catch(() => ({ items: [] })),
      // query needed requirements
      requirementApi.list(id, "query_needed=true&size=10").catch(() => ({ items: [] })),
      // recently reviewed
      requirementApi.list(id, "review_status=APPROVED&size=5").catch(() => ({ items: [] })),
      // needs review (NOT_REVIEWED + IN_REVIEW)
      requirementApi.list(id, "review_status=NOT_REVIEWED&size=10").catch(() => ({ items: [] })),
    ]).then(async ([docsArr, reqData, checklists, inquiries, jobsData, queryData, reviewedData, needsReviewData]) => {
      const docItems = docsArr as any[];
      const docs = docItems.length;
      const docsParsing = docItems.filter((d: any) => d.parse_status === "PARSING").length;
      const docsFailed = docItems.filter((d: any) => d.parse_status === "FAILED").length;

      const reqs = (reqData as any).items || [];
      const totalReqs = (reqData as any).total_count || reqs.length;

      // count review statuses
      const notReviewed = reqs.filter((r: any) => r.review_status === "NOT_REVIEWED").length;
      const inReview = reqs.filter((r: any) => r.review_status === "IN_REVIEW").length;
      const approved = reqs.filter((r: any) => r.review_status === "APPROVED").length;
      const hold = reqs.filter((r: any) => r.review_status === "HOLD").length;
      const needsUpdate = reqs.filter((r: any) => r.review_status === "NEEDS_UPDATE").length;
      const queryNeeded = reqs.filter((r: any) => r.query_needed).length;

      // checklist stats: load items for each checklist to get high-risk + mandatory stats
      const cls = checklists as any[];
      let mandatoryNotDone = 0;
      let highRiskItems = 0;
      let totalChecklistItems = 0;
      let doneChecklistItems = 0;
      const highRiskAll: any[] = [];

      await Promise.all(cls.map(async (cl: any) => {
        const items = await checklistApi.items(id, cl.id).catch(() => []);
        (items as any[]).forEach((item: any) => {
          totalChecklistItems++;
          if (item.current_status === "DONE") doneChecklistItems++;
          if (item.mandatory_flag && item.current_status !== "DONE") mandatoryNotDone++;
          if (item.risk_level === "HIGH") {
            highRiskItems++;
            highRiskAll.push({ ...item, checklist_title: cl.title });
          }
        });
      }));

      setStats({
        docs, docsParsing, docsFailed,
        totalReqs, notReviewed, inReview, approved, hold, needsUpdate, queryNeeded,
        mandatoryNotDone, highRiskItems, totalChecklistItems, doneChecklistItems,
        inquiries: (inquiries as any[]).length,
      });

      setJobs((jobsData as any).items || []);
      setQueryItems((queryData as any).items || []);
      setRecentReviewed((reviewedData as any).items || []);
      setHighRiskList(highRiskAll.slice(0, 10));
      setNeedsReviewItems((needsReviewData as any).items || []);

      // 품질 이슈 통계 로드
      requirementApi.qualityStats(id).then(setQualityStats).catch(() => setQualityStats(null));
    }).catch((e) => {
      setError(e.code === "FORBIDDEN" ? "이 프로젝트에 접근할 권한이 없습니다." : (e.message || "데이터를 불러올 수 없습니다."));
    }).finally(() => setLoading(false));
  };

  useEffect(() => { loadData(); }, [id]);

  if (loading) return <LoadingState variant="detail" />;
  if (error) return <ErrorState title={error} onRetry={loadData} />;
  if (!roleLoading && !role) return <EmptyState title="접근 권한이 없습니다" description="프로젝트 소유자에게 멤버 초대를 요청하세요." />;

  // active/recent jobs
  const activeJobs = jobs.filter((j) => j.status === "PENDING" || j.status === "RUNNING");
  const failedJobs = jobs.filter((j) => j.status === "FAILED");
  const reviewPct = stats.totalReqs > 0 ? Math.round((stats.approved / stats.totalReqs) * 100) : 0;
  const checklistPct = stats.totalChecklistItems > 0 ? Math.round((stats.doneChecklistItems / stats.totalChecklistItems) * 100) : 0;

  return (
    <div className="space-y-4">
      {/* ── 상단: 핵심 지표 카드 ─────────────────────────────────────── */}
      <div className="grid grid-cols-5 gap-2.5">
        <Link href={`/projects/${id}/requirements`}>
          <StatCard label="요구사항" value={stats.totalReqs}
            sub={`승인 ${stats.approved} / 미검토 ${stats.notReviewed}`}
            color="bg-indigo-50/60 text-indigo-700 hover:ring-2 hover:ring-indigo-200" />
        </Link>
        <StatCard label="검토 진행률" value={`${reviewPct}%`}
          sub={`${stats.approved}/${stats.totalReqs} 승인`}
          color="bg-emerald-50/60 text-emerald-700"
          bar={reviewPct} barColor="bg-emerald-400" />
        <Link href={`/projects/${id}/checklists`}>
          <StatCard label="체크리스트" value={`${checklistPct}%`}
            sub={`${stats.doneChecklistItems}/${stats.totalChecklistItems} 완료`}
            color="bg-violet-50/60 text-violet-700 hover:ring-2 hover:ring-violet-200"
            bar={checklistPct} barColor="bg-violet-400" />
        </Link>
        <Link href={`/projects/${id}/documents`}>
          <StatCard label="문서" value={stats.docs}
            sub={stats.docsFailed > 0 ? `실패 ${stats.docsFailed}` : stats.docsParsing > 0 ? `파싱 중 ${stats.docsParsing}` : "정상"}
            color={stats.docsFailed > 0 ? "bg-rose-50/60 text-rose-700 hover:ring-2 hover:ring-rose-200" : "bg-gray-50 text-gray-600 hover:ring-2 hover:ring-gray-200"} />
        </Link>
        <Link href={`/projects/${id}/inquiries`}>
          <StatCard label="질의" value={stats.inquiries}
            sub={stats.queryNeeded > 0 ? `질의 필요 ${stats.queryNeeded}` : "없음"}
            color={stats.queryNeeded > 0 ? "bg-amber-50/60 text-amber-700 hover:ring-2 hover:ring-amber-200" : "bg-gray-50 text-gray-600 hover:ring-2 hover:ring-gray-200"} />
        </Link>
      </div>

      {/* ── 시작 안내 ─────────────────────────────────────────────── */}
      {stats.totalReqs === 0 && stats.docs === 0 && (
        <div className="bg-indigo-50/50 border border-indigo-100 rounded-xl p-4 text-sm text-indigo-800">
          <div className="font-semibold mb-1">프로젝트 시작하기</div>
          <div className="text-indigo-600">
            1. <Link href={`/projects/${id}/documents`} className="underline">문서 탭</Link>에서 RFP PDF를 업로드하세요.{" "}
            2. 업로드 후 분석을 시작하면 요구사항이 자동 추출됩니다.
          </div>
        </div>
      )}
      {stats.totalReqs === 0 && stats.docs > 0 && jobs.length === 0 && (
        <div className="bg-amber-50/50 border border-amber-100 rounded-xl p-4 text-sm text-amber-800">
          <div className="font-semibold mb-1">분석 대기 중</div>
          <div className="text-amber-600">
            문서가 업로드되었습니다. <Link href={`/projects/${id}/documents`} className="underline">문서 탭</Link>에서 분석을 시작하세요.
          </div>
        </div>
      )}

      {/* ── 경고 배너 ───────────────────────────────────────────── */}
      {(stats.mandatoryNotDone > 0 || stats.highRiskItems > 0 || stats.queryNeeded > 0 || stats.needsUpdate > 0) && (
        <div className="bg-rose-50/50 border border-rose-100 rounded-xl p-4">
          <div className="text-xs font-semibold text-rose-600 mb-2">주의 필요 항목</div>
          <div className="flex gap-4 flex-wrap text-[11px]">
            {stats.mandatoryNotDone > 0 && (
              <Link href={`/projects/${id}/checklists`}
                className="flex items-center gap-1.5 text-rose-600 hover:underline">
                <span className="w-1.5 h-1.5 bg-rose-400 rounded-full" />
                필수 미완료 {stats.mandatoryNotDone}건
              </Link>
            )}
            {stats.highRiskItems > 0 && (
              <Link href={`/projects/${id}/checklists`}
                className="flex items-center gap-1.5 text-amber-600 hover:underline">
                <span className="w-1.5 h-1.5 bg-amber-400 rounded-full" />
                고위험 {stats.highRiskItems}건
              </Link>
            )}
            {stats.queryNeeded > 0 && (
              <Link href={`/projects/${id}/requirements?query_needed=true`}
                className="flex items-center gap-1.5 text-amber-600 hover:underline">
                <span className="w-1.5 h-1.5 bg-amber-400 rounded-full" />
                질의 필요 {stats.queryNeeded}건
              </Link>
            )}
            {stats.needsUpdate > 0 && (
              <Link href={`/projects/${id}/requirements?review_status=NEEDS_UPDATE`}
                className="flex items-center gap-1.5 text-rose-500 hover:underline">
                <span className="w-1.5 h-1.5 bg-rose-300 rounded-full" />
                수정 필요 {stats.needsUpdate}건
              </Link>
            )}
          </div>
        </div>
      )}

      {/* ── AI 분석 품질 현황 ──────────────────────────────────────── */}
      {qualityStats && qualityStats.total_requirement_count > 0 && (
        <AppCard>
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-xs font-semibold text-gray-600">AI 분석 품질</h3>
            <div className="flex items-center gap-2 text-[11px]">
              <span className="text-gray-400">
                검토필요 <b className="text-amber-600 tabular-nums">{qualityStats.review_needed_count}</b>/{qualityStats.total_requirement_count}
              </span>
              {qualityStats.by_severity?.CRITICAL > 0 && (
                <Link href={`/projects/${id}/requirements?quality_severity=CRITICAL`}
                  className="bg-rose-50 text-rose-600 border border-rose-100 px-1.5 py-0.5 rounded-md font-semibold hover:ring-1 hover:ring-rose-200 transition-all tabular-nums">
                  치명 {qualityStats.by_severity.CRITICAL}
                </Link>
              )}
              {qualityStats.by_severity?.MINOR > 0 && (
                <Link href={`/projects/${id}/requirements?quality_severity=MINOR`}
                  className="bg-amber-50 text-amber-600 border border-amber-100 px-1.5 py-0.5 rounded-md font-semibold hover:ring-1 hover:ring-amber-200 transition-all tabular-nums">
                  일반 {qualityStats.by_severity.MINOR}
                </Link>
              )}
            </div>
          </div>
          {qualityStats.by_code && qualityStats.by_code.length > 0 ? (
            <div className="space-y-1.5">
              {qualityStats.by_code.slice(0, 5).map((item: any) => (
                <Link key={item.code}
                  href={`/projects/${id}/requirements?quality_issue_code=${item.code}`}
                  className="flex items-center gap-2 text-[11px] hover:bg-gray-50/80 rounded-lg px-2 py-1.5 -mx-2 transition-colors">
                  <span className={`shrink-0 px-1.5 py-0.5 rounded-md text-[9px] font-semibold border ${
                    item.severity === "CRITICAL" ? "bg-rose-50 text-rose-600 border-rose-100" : "bg-amber-50 text-amber-600 border-amber-100"
                  }`}>
                    {item.severity === "CRITICAL" ? "치명" : "일반"}
                  </span>
                  <span className="flex-1 text-gray-600">{item.message}</span>
                  <span className="font-mono text-gray-300 text-[10px]">{item.code}</span>
                  <span className="font-semibold text-gray-500 min-w-[24px] text-right tabular-nums">{item.count}</span>
                </Link>
              ))}
            </div>
          ) : (
            <div className="text-xs text-gray-400 py-4 text-center">품질 이슈가 없습니다.</div>
          )}
        </AppCard>
      )}

      {/* ── 중단: 검토 상태 분포 + 분석 Job 상태 ────────────────────── */}
      <div className="grid grid-cols-2 gap-3">
        {/* 사람 검토 상태 분포 */}
        <AppCard>
          <SectionHeader title="사람 검토 현황" action={
            <Link href={`/projects/${id}/requirements`} className="text-[10px] text-gray-400 hover:text-indigo-600 transition-colors">전체 보기</Link>
          } />
          <div className="space-y-2">
            <ReviewBar label="미검토" count={stats.notReviewed} total={stats.totalReqs}
              color="bg-gray-300" href={`/projects/${id}/requirements?review_status=NOT_REVIEWED`} />
            <ReviewBar label="검토중" count={stats.inReview} total={stats.totalReqs}
              color="bg-amber-300" href={`/projects/${id}/requirements?review_status=IN_REVIEW`} />
            <ReviewBar label="승인" count={stats.approved} total={stats.totalReqs}
              color="bg-emerald-400" href={`/projects/${id}/requirements?review_status=APPROVED`} />
            <ReviewBar label="보류" count={stats.hold} total={stats.totalReqs}
              color="bg-amber-400" href={`/projects/${id}/requirements?review_status=HOLD`} />
            <ReviewBar label="수정필요" count={stats.needsUpdate} total={stats.totalReqs}
              color="bg-rose-400" href={`/projects/${id}/requirements?review_status=NEEDS_UPDATE`} />
          </div>
        </AppCard>

        {/* AI 분석 Job 상태 */}
        <AppCard>
          <SectionHeader title="AI 분석 상태" action={
            isOwner ? (
              <Link href={`/projects/${id}/documents`}>
                <AppButton size="sm">분석 시작</AppButton>
              </Link>
            ) : undefined
          } />

          {activeJobs.length > 0 && (
            <div className="mb-3">
              {activeJobs.map((j) => (
                <div key={j.id} className="flex items-center gap-2 bg-indigo-50/50 border border-indigo-100 rounded-lg px-3 py-2 mb-1.5 text-[11px]">
                  <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-pulse" />
                  <span className="font-mono text-xs">{j.job_type}</span>
                  <StatusBadge value={j.status} />
                  {j.status === "RUNNING" && <span className="text-xs text-gray-400">분석 진행 중...</span>}
                </div>
              ))}
            </div>
          )}

          {failedJobs.length > 0 && (
            <div className="mb-3">
              {failedJobs.map((j) => (
                <div key={j.id} className="flex items-center gap-2 bg-rose-50/50 border border-rose-100 rounded-lg px-3 py-2 mb-1.5 text-[11px]">
                  <span className="text-rose-500 text-[10px] font-semibold">실패</span>
                  <span className="font-mono text-xs">{j.job_type}</span>
                  <StatusBadge value="FAILED" />
                  {j.error_message && <span className="text-xs text-red-500 truncate flex-1">{j.error_message}</span>}
                  {isOwner && (
                    <Link href={`/projects/${id}/documents`}
                      className="text-xs text-blue-600 hover:underline ml-auto shrink-0">재시도</Link>
                  )}
                </div>
              ))}
            </div>
          )}

          {jobs.length > 0 ? (
            <div className="space-y-1 text-sm">
              {jobs.slice(0, 5).map((j) => (
                <div key={j.id} className="flex items-center justify-between py-1.5 border-b last:border-0">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs text-gray-500">{j.job_type}</span>
                    <StatusBadge value={j.status} />
                  </div>
                  <span className="text-xs text-gray-400">
                    {j.created_at ? new Date(j.created_at).toLocaleDateString("ko-KR") : ""}
                  </span>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-sm text-gray-400 py-4 text-center">
              분석 Job이 없습니다.
              {isOwner && " 문서 탭에서 분석을 시작하세요."}
            </div>
          )}
        </AppCard>
      </div>

      {/* ── 하단: 미리보기 탭 ──────────────────────────────────────────── */}
      <AppCard padding="sm" className="overflow-hidden">
        <div className="flex border-b">
          {([
            { key: "review" as const, label: "검토 필요", count: stats.notReviewed + stats.needsUpdate },
            { key: "checklist" as const, label: "누락 위험", count: stats.mandatoryNotDone + stats.highRiskItems },
            { key: "query" as const, label: "질의 필요", count: stats.queryNeeded },
          ]).map((t) => (
            <button key={t.key} onClick={() => setPreviewTab(t.key)}
              className={`px-4 py-2.5 text-sm border-b-2 transition-colors ${
                previewTab === t.key ? "border-blue-600 text-blue-600 font-medium" : "border-transparent text-gray-500 hover:text-gray-700"
              }`}>
              {t.label} {t.count > 0 && <span className="ml-1 text-xs bg-red-100 text-red-700 px-1.5 py-0.5 rounded-full">{t.count}</span>}
            </button>
          ))}
          {role && (
            <div className="ml-auto flex items-center gap-2 px-4 text-sm text-gray-400">
              <StatusBadge value={role} />
            </div>
          )}
        </div>

        <div className="p-4 max-h-64 overflow-auto">
          {previewTab === "review" && (
            <div className="space-y-1.5">
              {needsReviewItems.length > 0 ? needsReviewItems.map((r: any) => (
                <Link key={r.id} href={`/projects/${id}/requirements/${r.id}?tab=review`}
                  className="flex items-center gap-2 bg-gray-50 rounded px-3 py-2 text-sm hover:bg-blue-50 transition-colors">
                  <span className="font-mono text-xs text-gray-500">{r.requirement_code}</span>
                  <span className="truncate flex-1 text-xs">{r.title}</span>
                  <StatusBadge value={r.review_status || "NOT_REVIEWED"} />
                  <StatusBadge value={r.fact_level || "REVIEW_NEEDED"} />
                </Link>
              )) : (
                <div className="text-xs text-gray-400 text-center py-6">검토 필요 항목 없음</div>
              )}
              {stats.notReviewed > needsReviewItems.length && (
                <Link href={`/projects/${id}/requirements?review_status=NOT_REVIEWED`}
                  className="block text-xs text-blue-600 hover:underline text-center pt-2">
                  미검토 {stats.notReviewed}건 전체 보기
                </Link>
              )}
            </div>
          )}

          {previewTab === "checklist" && (
            <div className="space-y-1.5">
              {highRiskList.length > 0 ? highRiskList.map((item: any) => (
                <div key={item.id} className="flex items-center gap-2 bg-red-50 rounded px-3 py-2 text-sm">
                  <span className="font-mono text-xs text-red-600">{item.item_code}</span>
                  <span className="truncate flex-1 text-xs">{item.item_text}</span>
                  <StatusBadge value={item.risk_level} />
                  <StatusBadge value={item.current_status} />
                  {item.mandatory_flag && <span className="text-[10px] text-red-600 font-bold">필수</span>}
                  {item.linked_requirement_id && (
                    <Link href={`/projects/${id}/requirements/${item.linked_requirement_id}`}
                      className="text-[10px] text-blue-600 hover:underline shrink-0">요구사항</Link>
                  )}
                </div>
              )) : (
                <div className="text-xs text-gray-400 text-center py-6">누락 위험 항목 없음</div>
              )}
              <Link href={`/projects/${id}/checklists`}
                className="block text-xs text-blue-600 hover:underline text-center pt-2">
                체크리스트 전체 보기
              </Link>
            </div>
          )}

          {previewTab === "query" && (
            <div className="space-y-1.5">
              {queryItems.length > 0 ? queryItems.map((r: any) => (
                <Link key={r.id} href={`/projects/${id}/requirements/${r.id}`}
                  className="flex items-center gap-2 bg-orange-50 rounded px-3 py-2 text-sm hover:bg-orange-100 transition-colors">
                  <span className="font-mono text-xs text-orange-600">{r.requirement_code}</span>
                  <span className="truncate flex-1 text-xs">{r.title}</span>
                  <StatusBadge value={r.fact_level || "REVIEW_NEEDED"} />
                </Link>
              )) : (
                <div className="text-xs text-gray-400 text-center py-6">질의 필요 항목 없음</div>
              )}
              <Link href={`/projects/${id}/inquiries`}
                className="block text-xs text-blue-600 hover:underline text-center pt-2">
                질의응답 전체 보기
              </Link>
            </div>
          )}
        </div>
      </AppCard>
    </div>
  );
}

// ── Stat Card ────────────────────────────────────────────────────────
function StatCard({ label, value, sub, color, bar, barColor }: {
  label: string; value: string | number; sub: string; color: string;
  bar?: number; barColor?: string;
}) {
  return (
    <div className={`rounded-xl border border-gray-100 shadow-sm p-3.5 transition-all ${color}`}>
      <div className="text-xl font-bold tabular-nums">{value}</div>
      <div className="text-[11px] font-semibold mt-0.5">{label}</div>
      <div className="text-[10px] opacity-60 mt-1">{sub}</div>
      {bar !== undefined && (
        <div className="w-full bg-white/40 rounded-full h-1 mt-2">
          <div className={`h-1 rounded-full transition-all ${barColor}`}
            style={{ width: `${Math.min(bar, 100)}%` }} />
        </div>
      )}
    </div>
  );
}

function ReviewBar({ label, count, total, color, href }: {
  label: string; count: number; total: number; color: string; href: string;
}) {
  const pct = total > 0 ? (count / total) * 100 : 0;
  return (
    <Link href={href} className="flex items-center gap-2 group hover:bg-gray-50/80 rounded-lg px-1.5 py-1 transition-colors">
      <span className="text-[11px] text-gray-500 w-14">{label}</span>
      <div className="flex-1 bg-gray-100 rounded-full h-1.5">
        <div className={`h-1.5 rounded-full transition-all ${color}`} style={{ width: `${pct}%` }} />
      </div>
      <span className="text-[11px] text-gray-400 w-16 text-right tabular-nums group-hover:text-indigo-600">
        {count} ({pct.toFixed(0)}%)
      </span>
    </Link>
  );
}
