"use client";

interface LoadingStateProps {
  /** list: 목록 스켈레톤, detail: 상세 스켈레톤, compact: 한 줄 */
  variant?: "list" | "detail" | "compact";
  rows?: number;
}

export default function LoadingState({ variant = "compact", rows = 4 }: LoadingStateProps) {
  if (variant === "compact") {
    return (
      <div className="text-center text-gray-300 py-12 text-sm">
        <div className="inline-block w-4 h-4 border-2 border-gray-200 border-t-indigo-400 rounded-full animate-spin mr-2" />
        불러오는 중...
      </div>
    );
  }

  if (variant === "list") {
    return (
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm divide-y divide-gray-50">
        {Array.from({ length: rows }).map((_, i) => (
          <div key={i} className="px-4 py-3.5 flex items-center gap-3 animate-pulse">
            <div className="w-16 h-3 bg-gray-100 rounded" />
            <div className="flex-1 h-3 bg-gray-100 rounded" />
            <div className="w-12 h-3 bg-gray-100 rounded" />
            <div className="w-10 h-3 bg-gray-100 rounded" />
          </div>
        ))}
      </div>
    );
  }

  // detail
  return (
    <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5 space-y-4 animate-pulse">
      <div className="h-4 bg-gray-100 rounded w-1/3" />
      <div className="h-3 bg-gray-100 rounded w-full" />
      <div className="h-3 bg-gray-100 rounded w-4/5" />
      <div className="h-20 bg-gray-50 rounded-lg" />
      <div className="h-3 bg-gray-100 rounded w-2/3" />
    </div>
  );
}
