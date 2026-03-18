"use client";

import { useState } from "react";

interface Props {
  /** PDF URL (storagePath 기반) */
  url: string | null;
  /** 이동할 페이지 번호 (1-based) */
  page?: number;
}

/**
 * 브라우저 내장 PDF 뷰어 (iframe).
 * page prop이 변경되면 #page={n} 해시로 해당 페이지로 이동.
 *
 * TODO: 실제 S3/storage URL 연동 시 url 생성 로직 변경 필요
 */
export default function PdfViewer({ url, page }: Props) {
  if (!url) {
    return (
      <div className="flex items-center justify-center h-full bg-gray-100 rounded text-sm text-gray-400">
        PDF 파일을 선택하세요
      </div>
    );
  }

  // 브라우저 내장 PDF 뷰어는 #page=N 으로 페이지 이동 지원
  const src = page ? `${url}#page=${page}` : url;

  return (
    <iframe
      key={src}
      src={src}
      className="w-full h-full border rounded"
      title="PDF Viewer"
    />
  );
}
