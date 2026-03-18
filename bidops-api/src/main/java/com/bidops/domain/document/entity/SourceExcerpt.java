package com.bidops.domain.document.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

/**
 * 문서에서 OCR/파싱 단계에 추출된 원문 발췌 블록.
 * Requirement ↔ SourceExcerpt 는 RequirementSource(N:M) 테이블로 연결.
 *
 * openapi.yaml SourceExcerpt schema 기준.
 */
@Entity
@Table(name = "source_excerpts", indexes = {
        @Index(name = "idx_source_excerpts_document", columnList = "document_id"),
        @Index(name = "idx_source_excerpts_page",     columnList = "document_id, page_no")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class SourceExcerpt {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId;

    /** 페이지 번호 (1-based) */
    @Column(name = "page_no", nullable = false)
    private Integer pageNo;

    /**
     * 발췌 유형
     * openapi.yaml: PARAGRAPH | TABLE | LIST | HEADER | FOOTNOTE
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "excerpt_type", nullable = false, length = 20)
    private ExcerptType excerptType;

    /**
     * 조항 번호 등 앵커 레이블 (예: "3.1.2", "별첨2")
     * clauseRefs 조합의 원천값
     */
    @Column(name = "anchor_label", length = 100)
    private String anchorLabel;

    /** OCR 원문 텍스트 */
    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;

    /** 정규화된 텍스트 (공백·특수문자 제거 후) */
    @Column(name = "normalized_text", columnDefinition = "TEXT")
    private String normalizedText;

    /**
     * 페이지 내 위치 좌표 (JSON)
     * 예: {"x":0,"y":100,"w":600,"h":80}
     */
    @Column(name = "bbox_json", columnDefinition = "TEXT")
    private String bboxJson;

    // ── 내부 Enum ────────────────────────────────────────────────────────────
    public enum ExcerptType {
        PARAGRAPH, TABLE, LIST, HEADER, FOOTNOTE
    }
}
