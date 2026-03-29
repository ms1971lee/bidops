"use client";

const COLORS: Record<string, string> = {
  DRAFT: "bg-gray-100 text-gray-700", IN_PROGRESS: "bg-blue-100 text-blue-700",
  COMPLETED: "bg-green-100 text-green-700", ARCHIVED: "bg-gray-200 text-gray-500",
  NOT_REVIEWED: "bg-gray-100 text-gray-600", IN_REVIEW: "bg-yellow-100 text-yellow-700",
  APPROVED: "bg-green-100 text-green-700", HOLD: "bg-orange-100 text-orange-700",
  NEEDS_UPDATE: "bg-red-100 text-red-700",
  TODO: "bg-gray-100 text-gray-600", DONE: "bg-green-100 text-green-700",
  BLOCKED: "bg-red-100 text-red-700",
  PENDING: "bg-gray-100 text-gray-600", RUNNING: "bg-blue-100 text-blue-700",
  FAILED: "bg-red-100 text-red-700",
  SUBMITTED: "bg-blue-100 text-blue-700", ANSWERED: "bg-green-100 text-green-700",
  CLOSED: "bg-gray-200 text-gray-500",
  HIGH: "bg-red-100 text-red-700", MEDIUM: "bg-yellow-100 text-yellow-700",
  LOW: "bg-blue-100 text-blue-700", NONE: "bg-gray-100 text-gray-500",
  FACT: "bg-green-100 text-green-700", INFERENCE: "bg-yellow-100 text-yellow-700",
  REVIEW_NEEDED: "bg-orange-100 text-orange-700",
  OWNER: "bg-purple-100 text-purple-700", ADMIN: "bg-indigo-100 text-indigo-700",
  EDITOR: "bg-blue-100 text-blue-700", REVIEWER: "bg-teal-100 text-teal-700",
  VIEWER: "bg-gray-100 text-gray-600",
  EXTRACTED: "bg-gray-100 text-gray-600", ENRICHED: "bg-blue-100 text-blue-700",
  FUNCTIONAL: "bg-blue-100 text-blue-700", NON_FUNCTIONAL: "bg-indigo-100 text-indigo-700",
  SECURITY: "bg-red-100 text-red-700", PERFORMANCE: "bg-yellow-100 text-yellow-700",
  DELIVERABLE: "bg-green-100 text-green-700", SUBMISSION: "bg-orange-100 text-orange-700",
  REVIEW: "bg-yellow-100 text-yellow-700",
  CRITICAL: "bg-red-200 text-red-800", QUERY_NEEDED: "bg-orange-100 text-orange-700",
};

/** 접근성용 한글 라벨. 색상만으로 상태를 구분하지 않도록 텍스트 보조. */
const LABELS: Record<string, string> = {
  DRAFT: "초안", IN_PROGRESS: "진행중", COMPLETED: "완료", ARCHIVED: "보관",
  NOT_REVIEWED: "미검토", IN_REVIEW: "검토중", APPROVED: "승인", HOLD: "보류",
  NEEDS_UPDATE: "수정필요", TODO: "할일", DONE: "완료", BLOCKED: "차단",
  PENDING: "대기", RUNNING: "실행중", FAILED: "실패",
  SUBMITTED: "제출", ANSWERED: "답변완료", CLOSED: "종료",
  HIGH: "높음", MEDIUM: "중간", LOW: "낮음", NONE: "없음",
  FACT: "확정", INFERENCE: "추론", REVIEW_NEEDED: "검토필요",
  CRITICAL: "긴급", QUERY_NEEDED: "질의필요", REVIEW: "검토중",
};

export default function StatusBadge({ value, label }: { value: string; label?: string }) {
  const cls = COLORS[value] || "bg-gray-100 text-gray-600";
  const display = label || LABELS[value] || value;
  return (
    <span className={`inline-block px-2 py-0.5 text-xs font-medium rounded ${cls}`}
      role="status" aria-label={display}>
      {display}
    </span>
  );
}
