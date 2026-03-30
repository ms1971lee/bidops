"use client";

interface EmptyStateProps {
  title: string;
  description?: string;
  primaryAction?: { label: string; onClick: () => void };
  secondaryAction?: { label: string; onClick: () => void };
  compact?: boolean;
}

export default function EmptyState({ title, description, primaryAction, secondaryAction, compact }: EmptyStateProps) {
  return (
    <div className={`bg-white rounded-xl border border-gray-100 shadow-sm text-center ${compact ? "py-8 px-4" : "py-14 px-6"}`}>
      <div className={`text-gray-400 ${compact ? "text-sm" : "text-sm"} mb-1`}>{title}</div>
      {description && <div className="text-[11px] text-gray-300 mt-1">{description}</div>}
      {(primaryAction || secondaryAction) && (
        <div className={`flex items-center justify-center gap-3 ${compact ? "mt-3" : "mt-5"}`}>
          {primaryAction && (
            <button onClick={primaryAction.onClick}
              className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 transition-colors shadow-sm">
              {primaryAction.label}
            </button>
          )}
          {secondaryAction && (
            <button onClick={secondaryAction.onClick}
              className="text-[11px] text-gray-400 hover:text-indigo-600 transition-colors">
              {secondaryAction.label}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
