"use client";

import StatusBadge from "./StatusBadge";

interface Props {
  title: string;
  badges?: { value: string; label?: string }[];
  meta?: React.ReactNode;
  actions?: React.ReactNode;
  onClose: () => void;
  children: React.ReactNode;
}

export default function DetailPanel({ title, badges, meta, actions, onClose, children }: Props) {
  return (
    <div className="flex-1 bg-white rounded border overflow-auto min-w-0"
      role="complementary" aria-label={`${title} 상세`}>
      <div className="p-5 space-y-4 text-sm">
        {/* 헤더 */}
        <div className="flex items-start justify-between">
          <div>
            <h3 className="font-semibold text-base">{title}</h3>
            {badges && badges.length > 0 && (
              <div className="flex gap-1.5 mt-1 flex-wrap">
                {badges.map((b, i) => <StatusBadge key={i} value={b.value} label={b.label} />)}
              </div>
            )}
            {meta && <div className="mt-1">{meta}</div>}
          </div>
          <div className="flex gap-2 items-center shrink-0">
            {actions}
            <button onClick={onClose} aria-label="패널 닫기"
              className="text-gray-400 hover:text-gray-600 text-lg leading-none">&times;</button>
          </div>
        </div>

        {/* 본문 */}
        {children}
      </div>
    </div>
  );
}

/** 상세 패널 내 필드 섹션 */
export function PanelField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-xs text-gray-500 mb-1 font-medium">{label}</div>
      {children}
    </div>
  );
}
