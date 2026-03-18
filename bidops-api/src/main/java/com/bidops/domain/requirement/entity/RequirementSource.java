package com.bidops.domain.requirement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

/**
 * 요구사항 ↔ 원문 발췌(SourceExcerpt) N:M 연결 테이블.
 *
 * 하나의 요구사항은 여러 페이지/조항에 걸쳐 근거를 가질 수 있고,
 * 하나의 발췌 블록이 여러 요구사항의 근거가 될 수도 있다.
 *
 * pageRefs, clauseRefs 는 이 테이블 → SourceExcerpt 를 통해 동적으로 조합.
 */
@Entity
@Table(
    name = "requirement_sources",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_req_source",
        columnNames = {"requirement_id", "source_excerpt_id"}
    ),
    indexes = {
        @Index(name = "idx_req_sources_req",  columnList = "requirement_id"),
        @Index(name = "idx_req_sources_excr", columnList = "source_excerpt_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class RequirementSource {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "requirement_id", nullable = false, length = 36)
    private String requirementId;

    @Column(name = "source_excerpt_id", nullable = false, length = 36)
    private String sourceExcerptId;

    /**
     * 이 발췌 블록이 해당 요구사항의 어떤 역할을 하는지.
     * PRIMARY: 핵심 원문, SUPPORTING: 보조 근거
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 20)
    @Builder.Default
    private LinkType linkType = LinkType.PRIMARY;

    public enum LinkType {
        PRIMARY,    // 핵심 원문 근거
        SUPPORTING  // 보조/참조 근거
    }
}
