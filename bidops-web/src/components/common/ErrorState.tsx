"use client";

interface ErrorStateProps {
  title?: string;
  description?: string;
  onRetry?: () => void;
  compact?: boolean;
}

export default function ErrorState({
  title = "데이터를 불러오지 못했습니다.",
  description = "잠시 후 다시 시도해주세요.",
  onRetry,
  compact,
}: ErrorStateProps) {
  if (compact) {
    return (
      <div className="bg-rose-50/50 border border-rose-100 rounded-xl px-4 py-2.5 flex items-center justify-between text-sm text-rose-600">
        <span>{title}</span>
        {onRetry && (
          <button onClick={onRetry} className="text-[11px] text-rose-500 hover:underline font-medium ml-3">
            다시 시도
          </button>
        )}
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-gray-100 shadow-sm text-center py-14 px-6">
      <div className="w-10 h-10 bg-rose-50 rounded-xl flex items-center justify-center mx-auto mb-3">
        <span className="text-rose-400 text-lg">!</span>
      </div>
      <div className="text-sm text-gray-600 mb-1">{title}</div>
      {description && <div className="text-[11px] text-gray-300">{description}</div>}
      {onRetry && (
        <button onClick={onRetry}
          className="mt-4 px-4 py-2 bg-white border border-gray-200 text-sm text-gray-600 rounded-lg hover:border-indigo-200 hover:text-indigo-600 transition-colors">
          다시 시도
        </button>
      )}
    </div>
  );
}
