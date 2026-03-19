"use client";

export interface BboxHighlight {
  /** 페이지 내 x 좌표 (%) */
  x: number;
  /** 페이지 내 y 좌표 (%) */
  y: number;
  /** 너비 (%) */
  w: number;
  /** 높이 (%) */
  h: number;
  /** 표시 라벨 (예: anchorLabel) */
  label?: string;
}

interface Props {
  url: string | null;
  page?: number;
  /** bbox 하이라이트. null이면 표시 안 함 */
  highlight?: BboxHighlight | null;
}

/**
 * PDF 뷰어 + bbox 하이라이트 오버레이.
 *
 * - iframe 기반 브라우저 내장 PDF 뷰어 사용
 * - #page=N 해시로 페이지 이동
 * - highlight prop이 있으면 반투명 박스 오버레이 표시
 * - bboxJson 형식: {"x":10,"y":20,"w":80,"h":5} (% 단위)
 *
 * TODO: 실제 S3 URL 연동 시 url 생성 로직 변경 필요
 * TODO: pdf.js 기반 뷰어로 전환 시 정확한 좌표 매핑 가능
 */
export default function PdfViewer({ url, page, highlight }: Props) {
  if (!url) {
    return (
      <div className="flex items-center justify-center h-full bg-gray-100 rounded text-sm text-gray-400">
        PDF 파일을 선택하세요
      </div>
    );
  }

  const src = page ? url + "#page=" + page : url;

  return (
    <div className="relative w-full h-full">
      <iframe
        key={src}
        src={src}
        className="w-full h-full border rounded"
        title="PDF Viewer"
      />

      {/* bbox 하이라이트 오버레이 */}
      {highlight && (
        <div
          className="absolute pointer-events-none border-2 border-orange-500 bg-orange-200/30 rounded"
          style={{
            left: highlight.x + "%",
            top: highlight.y + "%",
            width: highlight.w + "%",
            height: highlight.h + "%",
          }}
        >
          {highlight.label && (
            <span className="absolute -top-5 left-0 text-[10px] bg-orange-500 text-white px-1.5 py-0.5 rounded whitespace-nowrap">
              {highlight.label}
            </span>
          )}
        </div>
      )}

      {/* bbox 없이 페이지 이동만 한 경우 안내 */}
      {page && !highlight && (
        <div className="absolute top-2 right-2 bg-blue-600 text-white text-[10px] px-2 py-1 rounded shadow">
          p.{page} 이동됨
        </div>
      )}
    </div>
  );
}

/**
 * bboxJson 문자열을 BboxHighlight로 파싱.
 * 파싱 실패 시 null 반환 (graceful fallback).
 */
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
