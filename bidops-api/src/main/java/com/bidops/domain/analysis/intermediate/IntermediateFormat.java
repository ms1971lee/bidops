package com.bidops.domain.analysis.intermediate;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * PDF 분석 중간 포맷. 4종 묶음을 메모리에 보관.
 * normalized.md + block_index + requirement_catalog + 메타정보.
 */
@Getter
@Builder
public class IntermediateFormat {

    private String documentId;
    private String projectId;
    private String title;
    private int pageCount;
    private String sourceFileName;

    /** normalized.md 내용 (AI 입력용) */
    private String normalizedMarkdown;

    /** 블록 인덱스 */
    private List<Block> blocks;

    /** 요구사항 카탈로그 (기대 목록) */
    private List<CatalogItem> catalogItems;

    /** 카테고리별 기대 건수 */
    private Map<String, Integer> expectedBreakdown;

    /** 기대 총 건수 */
    private int expectedTotal;

    @Getter @Builder
    public static class Block {
        private String blockId;
        private int pageNo;
        private String excerptType; // PARAGRAPH, TABLE, LIST, HEADER
        private String clauseId;    // MAR-001 등
        private String rawText;
        private boolean requirementCandidate;
        private List<String> requirementSignals;
    }

    @Getter @Builder
    public static class CatalogItem {
        private String originalRequirementNo; // MAR-001
        private String groupCode;             // MAR
        private int sequenceNo;               // 1
        private String originalTitle;
        private int pageNo;
        private String sourceBlockId;
        private String status; // DETECTED, UNCERTAIN
    }
}
