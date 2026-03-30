"use client";

const COLORS: Record<string, string> = {
  // 프로젝트/작업 상태
  DRAFT: "bg-gray-50 text-gray-500 border-gray-200",
  ANALYZING: "bg-indigo-50/60 text-indigo-600 border-indigo-100",
  REVIEWING: "bg-amber-50/60 text-amber-600 border-amber-100",
  SUBMISSION_PREP: "bg-violet-50/60 text-violet-600 border-violet-100",
  IN_PROGRESS: "bg-indigo-50/60 text-indigo-600 border-indigo-100",
  COMPLETED: "bg-emerald-50/60 text-emerald-600 border-emerald-100",
  ARCHIVED: "bg-gray-50 text-gray-400 border-gray-200",
  CLOSED: "bg-gray-50 text-gray-400 border-gray-200",

  // 검토 상태
  NOT_REVIEWED: "bg-gray-50 text-gray-500 border-gray-200",
  IN_REVIEW: "bg-amber-50/60 text-amber-600 border-amber-100",
  APPROVED: "bg-emerald-50/60 text-emerald-600 border-emerald-100",
  HOLD: "bg-amber-50/60 text-amber-600 border-amber-100",
  NEEDS_UPDATE: "bg-rose-50/60 text-rose-600 border-rose-100",

  // 할일 상태
  TODO: "bg-gray-50 text-gray-500 border-gray-200",
  DONE: "bg-emerald-50/60 text-emerald-600 border-emerald-100",
  BLOCKED: "bg-rose-50/60 text-rose-600 border-rose-100",

  // Job 상태
  PENDING: "bg-gray-50 text-gray-500 border-gray-200",
  RUNNING: "bg-indigo-50/60 text-indigo-600 border-indigo-100",
  FAILED: "bg-rose-50/60 text-rose-600 border-rose-100",

  // 질의 상태
  SUBMITTED: "bg-indigo-50/60 text-indigo-600 border-indigo-100",
  ANSWERED: "bg-emerald-50/60 text-emerald-600 border-emerald-100",

  // 위험도
  HIGH: "bg-rose-50/60 text-rose-600 border-rose-100",
  MEDIUM: "bg-amber-50/60 text-amber-600 border-amber-100",
  LOW: "bg-indigo-50/60 text-indigo-600 border-indigo-100",
  NONE: "bg-gray-50 text-gray-400 border-gray-200",

  // 근거 수준
  FACT: "bg-emerald-50/60 text-emerald-600 border-emerald-100",
  INFERENCE: "bg-amber-50/60 text-amber-600 border-amber-100",
  REVIEW_NEEDED: "bg-amber-50/60 text-amber-600 border-amber-100",

  // 역할
  OWNER: "bg-violet-50/60 text-violet-600 border-violet-100",
  ADMIN: "bg-indigo-50/60 text-indigo-600 border-indigo-100",
  EDITOR: "bg-indigo-50/60 text-indigo-600 border-indigo-100",
  REVIEWER: "bg-teal-50/60 text-teal-600 border-teal-100",
  VIEWER: "bg-gray-50 text-gray-500 border-gray-200",

  // 분석 상태
  EXTRACTED: "bg-gray-50 text-gray-500 border-gray-200",
  ENRICHED: "bg-indigo-50/60 text-indigo-600 border-indigo-100",

  // 카테고리 (주요)
  FUNCTIONAL: "bg-indigo-50/60 text-indigo-600 border-indigo-100",
  NON_FUNCTIONAL: "bg-violet-50/60 text-violet-600 border-violet-100",
  SECURITY: "bg-rose-50/60 text-rose-600 border-rose-100",
  PERFORMANCE: "bg-amber-50/60 text-amber-600 border-amber-100",
  DELIVERABLE: "bg-emerald-50/60 text-emerald-600 border-emerald-100",
  SUBMISSION: "bg-amber-50/60 text-amber-600 border-amber-100",

  // 문서 상태
  UPLOADED: "bg-indigo-50/60 text-indigo-600 border-indigo-100",
  PARSING: "bg-amber-50/60 text-amber-600 border-amber-100",
  PARSED: "bg-emerald-50/60 text-emerald-600 border-emerald-100",

  // 품질
  CRITICAL: "bg-rose-50/60 text-rose-600 border-rose-100",
  QUERY_NEEDED: "bg-amber-50/60 text-amber-600 border-amber-100",
};

const LABELS: Record<string, string> = {
  DRAFT: "초안", ANALYZING: "분석중", REVIEWING: "검토중", SUBMISSION_PREP: "제출준비",
  IN_PROGRESS: "진행중", COMPLETED: "완료", ARCHIVED: "보관", CLOSED: "종료",
  NOT_REVIEWED: "미검토", IN_REVIEW: "검토중", APPROVED: "승인", HOLD: "보류",
  NEEDS_UPDATE: "수정필요", TODO: "할일", DONE: "완료", BLOCKED: "차단",
  PENDING: "대기", RUNNING: "실행중", FAILED: "실패",
  SUBMITTED: "제출", ANSWERED: "답변완료",
  HIGH: "높음", MEDIUM: "중간", LOW: "낮음", NONE: "없음",
  FACT: "확정", INFERENCE: "추론", REVIEW_NEEDED: "검토필요",
  CRITICAL: "긴급", QUERY_NEEDED: "질의필요",
  UPLOADED: "업로드", PARSING: "분석중", PARSED: "분석완료",
  RFP: "RFP", ANNEX: "별첨", FORM: "양식", QNA: "Q&A", PROPOSAL_REFERENCE: "참고",
};

export default function StatusBadge({ value, label }: { value: string; label?: string }) {
  const cls = COLORS[value] || "bg-gray-50 text-gray-500 border-gray-200";
  const display = label || LABELS[value] || value;
  return (
    <span className={`inline-block px-1.5 py-0.5 text-[10px] font-medium rounded-md border ${cls}`}
      role="status" aria-label={display}>
      {display}
    </span>
  );
}
