package com.bidops.domain.document.repository;

import com.bidops.domain.document.entity.SourceExcerpt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface SourceExcerptRepository extends JpaRepository<SourceExcerpt, String> {

    /**
     * documentId + pageNo + anchorLabel 조합으로 기존 발췌 블록 조회.
     * RFP 분석 결과 저장 시 중복 SourceExcerpt 생성 방지에 사용.
     */
    Optional<SourceExcerpt> findByDocumentIdAndPageNoAndAnchorLabel(
            String documentId, Integer pageNo, String anchorLabel);

    List<SourceExcerpt> findByDocumentIdOrderByPageNoAsc(String documentId);

    /**
     * ID 목록으로 일괄 조회 (pageNo 오름차순).
     *
     * 요구사항 근거 조회 흐름 (도메인 경계 준수):
     *   1. RequirementSourceRepository.findByRequirementId()   → sourceExcerptId 목록
     *   2. 이 메서드로 SourceExcerpt 일괄 조회
     *
     * document 도메인이 requirement 도메인(RequirementSource)을
     * 직접 JPQL 참조하지 않도록 분리한다.
     */
    @Query("SELECT se FROM SourceExcerpt se WHERE se.id IN :ids ORDER BY se.pageNo ASC, se.id ASC")
    List<SourceExcerpt> findAllByIdInOrdered(@Param("ids") List<String> ids);

    @Modifying @Transactional
    void deleteByDocumentId(String documentId);
}
