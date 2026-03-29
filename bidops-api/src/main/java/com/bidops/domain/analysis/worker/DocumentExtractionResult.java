package com.bidops.domain.analysis.worker;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 문서 텍스트 추출 결과.
 * 페이지별 텍스트 + 단락별 메타데이터를 포함하여
 * AI 분석의 page_no 정확도와 SourceExcerpt 품질을 높인다.
 */
@Getter
@Builder
public class DocumentExtractionResult {

    /** 전체 텍스트 (페이지 구분 마커 포함) */
    private final String fullText;

    /** 총 페이지 수 */
    private final int totalPages;

    /** 페이지별 텍스트 */
    private final List<PageText> pages;

    /** 추출 방법 (PDFBOX, AZURE_DI) */
    private final String extractionMethod;

    /** 추출 통계 */
    private final ExtractionStats stats;

    @Getter
    @Builder
    public static class PageText {
        private final int pageNo;
        private final String text;
        private final int charCount;
        private final boolean hasTable;
        private final boolean isEmpty;
        /** 이 페이지의 단락 목록 (Azure DI에서만 상세 제공) */
        private final List<Paragraph> paragraphs;
    }

    /** 단락 수준 정보 — SourceExcerpt 생성에 직접 사용 */
    @Getter
    @Builder
    public static class Paragraph {
        private final int pageNo;
        private final String text;
        /** PARAGRAPH, TABLE, LIST, HEADER, FOOTNOTE */
        private final String type;
        /** 조항 번호 (자동 감지 또는 Azure DI role) */
        private final String anchorLabel;
        /** bbox JSON (Azure DI에서 제공) */
        private final String bboxJson;
    }

    /** 추출 품질 통계 */
    @Getter
    @Builder
    public static class ExtractionStats {
        private final int totalChars;
        private final int totalParagraphs;
        private final int tableCount;
        private final int emptyPageCount;
        private final int headerCount;
        private final int listCount;
    }
}
