"use client";

import { useState } from "react";

export interface BboxHighlight {
  x: number;
  y: number;
  w: number;
  h: number;
  label?: string;
  type?: "primary" | "supporting" | "table";
}

export interface SourceBlock {
  id: string;
  page_no: number;
  excerpt_type?: string;
  anchor_label?: string;
  raw_text?: string;
  bbox_json?: string;
  link_type?: string;
  normalized_text?: string;
}

interface Props {
  url: string | null;
  page?: number;
  highlight?: BboxHighlight | null;
  /** 현재 페이지의 모든 근거 블록 (멀티 하이라이트용) */
  pageBlocks?: SourceBlock[];
  /** 현재 활성화된 블록 ID */
  activeBlockId?: string | null;
  /** 블록 클릭 핸들러 */
  onBlockClick?: (block: SourceBlock) => void;
  /** 페이지 변경 콜백 (prev/next 버튼) */
  onPageChange?: (page: number) => void;
  /** 최대 페이지 수 (알 수 있을 때) */
  maxPage?: number;
}

/**
 * PDF 뷰어 + 근거 하이라이트 오버레이.
 *
 * 3가지 하이라이트 모드:
 * 1. bbox 있음 (Azure DI) → 정확한 위치에 반투명 박스 표시
 * 2. bbox 없음 + anchor 있음 → 페이지 이동 + anchor 라벨 배지
 * 3. bbox/anchor 모두 없음 → 페이지 이동 + "p.N" 안내
 */
export default function PdfViewer({ url, page, highlight, pageBlocks, activeBlockId, onBlockClick, onPageChange, maxPage }: Props) {
  const [iframeError, setIframeError] = useState(false);

  if (!url) {
    return (
      <div className="flex items-center justify-center h-full bg-gray-100 rounded text-sm text-gray-400">
        PDF 파일을 선택하세요
      </div>
    );
  }

  const src = page ? url + "#page=" + page : url;
  const canPrev = page && page > 1;
  const canNext = page && (!maxPage || page < maxPage);

  // 같은 페이지의 모든 bbox 블록을 멀티 하이라이트
  const bboxBlocks = (pageBlocks || []).filter(
    (b) => b.page_no === page && b.bbox_json
  );

  return (
    <div className="relative w-full h-full">
      {iframeError ? (
        <div className="w-full h-full border rounded bg-gray-50 flex flex-col items-center justify-center gap-3">
          <div className="text-sm text-gray-500">PDF를 임베드할 수 없습니다</div>
          <a href={src} target="_blank" rel="noopener noreferrer"
            className="px-4 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 transition-colors">
            새 탭에서 PDF 열기
          </a>
          {page && <div className="text-xs text-gray-400">p.{page}</div>}
        </div>
      ) : (
        <iframe key={src} src={src} className="w-full h-full border rounded" title="PDF Viewer"
          onError={() => setIframeError(true)}
          onLoad={(e) => {
            try {
              const frame = e.currentTarget;
              if (frame.contentDocument?.title === "") setIframeError(false);
            } catch { /* cross-origin, iframe loaded successfully */ }
          }} />
      )}

      {/* 페이지 이동 버튼 */}
      {onPageChange && page && (
        <div className="absolute top-1 left-1/2 -translate-x-1/2 z-30 flex items-center gap-1 bg-white/90 rounded shadow-sm px-1 py-0.5">
          <button onClick={() => canPrev && onPageChange(page - 1)}
            disabled={!canPrev}
            className="px-1.5 py-0.5 text-xs rounded hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed"
            aria-label="이전 페이지">
            &larr;
          </button>
          <span className="text-[10px] text-gray-600 font-mono min-w-[32px] text-center">
            p.{page}{maxPage ? `/${maxPage}` : ""}
          </span>
          <button onClick={() => canNext && onPageChange(page + 1)}
            disabled={!canNext}
            className="px-1.5 py-0.5 text-xs rounded hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed"
            aria-label="다음 페이지">
            &rarr;
          </button>
        </div>
      )}

      {/* 멀티 bbox 하이라이트 (같은 페이지의 모든 근거) */}
      {bboxBlocks.map((block) => {
        const bbox = parseBboxJson(block.bbox_json);
        if (!bbox) return null;
        const isActive = block.id === activeBlockId;
        return (
          <div key={block.id}
            className={`absolute pointer-events-auto cursor-pointer rounded transition-all ${
              isActive
                ? "border-2 border-orange-500 bg-orange-200/40 z-20"
                : "border border-orange-300/60 bg-orange-100/20 hover:bg-orange-200/30 z-10"
            }`}
            style={{ left: bbox.x + "%", top: bbox.y + "%", width: bbox.w + "%", height: bbox.h + "%" }}
            onClick={() => onBlockClick?.(block)}
            title={block.anchor_label || block.excerpt_type || ""}>
            {isActive && block.anchor_label && (
              <span className="absolute -top-5 left-0 text-[10px] bg-orange-500 text-white px-1.5 py-0.5 rounded whitespace-nowrap shadow">
                {block.anchor_label}
              </span>
            )}
            {isActive && !block.anchor_label && block.excerpt_type && (
              <span className="absolute -top-5 left-0 text-[10px] bg-gray-600 text-white px-1.5 py-0.5 rounded whitespace-nowrap shadow">
                {EXCERPT_TYPE_LABELS[block.excerpt_type] || block.excerpt_type}
              </span>
            )}
          </div>
        );
      })}

      {/* 단일 하이라이트 (멀티 블록이 없을 때 fallback) */}
      {highlight && bboxBlocks.length === 0 && (
        <div
          className="absolute pointer-events-none border-2 border-orange-500 bg-orange-200/30 rounded z-20"
          style={{ left: highlight.x + "%", top: highlight.y + "%", width: highlight.w + "%", height: highlight.h + "%" }}>
          {highlight.label && (
            <span className="absolute -top-5 left-0 text-[10px] bg-orange-500 text-white px-1.5 py-0.5 rounded whitespace-nowrap shadow">
              {highlight.label}
            </span>
          )}
        </div>
      )}

      {/* bbox 없이 페이지 이동만 한 경우 — fallback: pageNo + anchor 배지 */}
      {page && !highlight && bboxBlocks.length === 0 && (() => {
        const noBboxBlocks = (pageBlocks || []).filter(
          (b) => b.page_no === page && !b.bbox_json
        );
        const anchor = noBboxBlocks.find((b) => b.id === activeBlockId)?.anchor_label
          || noBboxBlocks[0]?.anchor_label || null;
        return (
          <>
            <PageFallbackBadge page={page} anchor={anchor} />
            <div className="absolute bottom-2 left-2 right-2 pointer-events-none">
              <div className="text-[10px] text-gray-400 text-center">
                정확한 위치 표시는 Azure Document Intelligence 분석이 필요합니다
              </div>
            </div>
          </>
        );
      })()}
    </div>
  );
}

function PageFallbackBadge({ page, anchor }: { page: number; anchor: string | null }) {
  return (
    <div className="absolute top-8 right-2 flex items-center gap-1.5 z-30">
      <span className="bg-blue-600 text-white text-[10px] px-2 py-1 rounded shadow">
        p.{page}
      </span>
      {anchor && (
        <span className="bg-gray-700 text-white text-[10px] px-2 py-1 rounded shadow">
          {anchor}
        </span>
      )}
    </div>
  );
}

const EXCERPT_TYPE_LABELS: Record<string, string> = {
  PARAGRAPH: "문단",
  TABLE: "표",
  LIST: "목록",
  HEADER: "제목",
  FOOTNOTE: "각주",
};

/** bbox JSON 파싱 */
export function parseBboxJson(bboxJson: string | null | undefined, label?: string): BboxHighlight | null {
  if (!bboxJson) return null;
  try {
    const parsed = JSON.parse(bboxJson);
    if (typeof parsed.x === "number" && typeof parsed.y === "number"
        && typeof parsed.w === "number" && typeof parsed.h === "number") {
      return { x: parsed.x, y: parsed.y, w: parsed.w, h: parsed.h, label };
    }
    return null;
  } catch {
    return null;
  }
}

/** excerptType에 따른 하이라이트 스타일 */
export function excerptTypeStyle(type?: string): string {
  switch (type) {
    case "TABLE": return "border-l-4 border-l-purple-400 bg-purple-50";
    case "HEADER": return "border-l-4 border-l-blue-400 bg-blue-50";
    case "LIST": return "border-l-4 border-l-green-400 bg-green-50";
    case "FOOTNOTE": return "border-l-4 border-l-gray-400 bg-gray-50";
    default: return "bg-blue-50 border border-blue-100";
  }
}
