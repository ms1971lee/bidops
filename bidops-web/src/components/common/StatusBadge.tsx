"use client";

const COLORS: Record<string, string> = {
  // project
  DRAFT: "bg-gray-100 text-gray-700",
  IN_PROGRESS: "bg-blue-100 text-blue-700",
  COMPLETED: "bg-green-100 text-green-700",
  ARCHIVED: "bg-gray-200 text-gray-500",
  // requirement review
  NOT_REVIEWED: "bg-gray-100 text-gray-600",
  IN_REVIEW: "bg-yellow-100 text-yellow-700",
  APPROVED: "bg-green-100 text-green-700",
  HOLD: "bg-orange-100 text-orange-700",
  NEEDS_UPDATE: "bg-red-100 text-red-700",
  // checklist
  TODO: "bg-gray-100 text-gray-600",
  DONE: "bg-green-100 text-green-700",
  BLOCKED: "bg-red-100 text-red-700",
  // analysis job
  PENDING: "bg-gray-100 text-gray-600",
  RUNNING: "bg-blue-100 text-blue-700",
  FAILED: "bg-red-100 text-red-700",
  // inquiry
  SUBMITTED: "bg-blue-100 text-blue-700",
  ANSWERED: "bg-green-100 text-green-700",
  CLOSED: "bg-gray-200 text-gray-500",
  // risk
  HIGH: "bg-red-100 text-red-700",
  MEDIUM: "bg-yellow-100 text-yellow-700",
  LOW: "bg-blue-100 text-blue-700",
  NONE: "bg-gray-100 text-gray-500",
  // fact level
  FACT: "bg-green-100 text-green-700",
  INFERENCE: "bg-yellow-100 text-yellow-700",
  REVIEW_NEEDED: "bg-orange-100 text-orange-700",
};

export default function StatusBadge({ value }: { value: string }) {
  const cls = COLORS[value] || "bg-gray-100 text-gray-600";
  return (
    <span className={`inline-block px-2 py-0.5 text-xs font-medium rounded ${cls}`}>
      {value}
    </span>
  );
}
