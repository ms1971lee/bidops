"use client";

import { useState } from "react";

export interface FilterOption {
  value: string;
  label: string;
}

interface Props {
  /** 버튼 토글 필터 (상태 등) */
  toggleFilters?: {
    label: string;
    options: FilterOption[];
    value: string;
    onChange: (v: string) => void;
  }[];
  /** 드롭다운 필터 */
  selectFilters?: {
    label: string;
    options: FilterOption[];
    value: string;
    onChange: (v: string) => void;
  }[];
  /** 키워드 검색 */
  keyword?: {
    value: string;
    onChange: (v: string) => void;
    onSearch: () => void;
    placeholder?: string;
  };
  /** 초기화 버튼 표시 여부 */
  hasActiveFilter?: boolean;
  onReset?: () => void;
  /** 우측 슬롯 (버튼 등) */
  actions?: React.ReactNode;
  /** 총 건수 */
  totalCount?: number;
}

export default function FilterBar({
  toggleFilters, selectFilters, keyword, hasActiveFilter, onReset, actions, totalCount,
}: Props) {
  return (
    <div className="bg-white rounded border p-3 mb-3 space-y-2" role="search" aria-label="필터">
      <div className="flex items-center gap-2 flex-wrap">
        {toggleFilters?.map((tf, i) => (
          <div key={i} className="flex gap-1 flex-wrap items-center">
            {i > 0 && <span className="text-gray-300 mx-1">|</span>}
            {tf.options.map((opt) => (
              <button key={opt.value} onClick={() => tf.onChange(opt.value)}
                aria-pressed={tf.value === opt.value}
                className={`px-2.5 py-1 text-xs rounded border transition-colors ${
                  tf.value === opt.value ? "bg-blue-600 text-white border-blue-600" : "bg-white hover:bg-gray-50"
                }`}>
                {opt.label}
              </button>
            ))}
          </div>
        ))}

        {selectFilters?.map((sf, i) => (
          <select key={i} value={sf.value} onChange={(e) => sf.onChange(e.target.value)}
            aria-label={sf.label}
            className="border rounded px-2 py-1 text-xs">
            {sf.options.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
        ))}

        {keyword && (
          <form onSubmit={(e) => { e.preventDefault(); keyword.onSearch(); }}
            className="flex gap-1 ml-auto">
            <input value={keyword.value} onChange={(e) => keyword.onChange(e.target.value)}
              className="border rounded px-2 py-1 text-xs w-40"
              placeholder={keyword.placeholder || "검색..."}
              aria-label="키워드 검색" />
            <button type="submit"
              className="px-2 py-1 bg-gray-100 border rounded text-xs hover:bg-gray-200">검색</button>
          </form>
        )}

        {hasActiveFilter && onReset && (
          <button onClick={onReset} className="text-xs text-gray-400 hover:text-red-500"
            aria-label="필터 초기화">초기화</button>
        )}

        {totalCount !== undefined && (
          <span className="text-xs text-gray-400 ml-auto">{totalCount}건</span>
        )}

        {actions}
      </div>
    </div>
  );
}
