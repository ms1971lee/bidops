package com.bidops.domain.document.repository;

import com.bidops.domain.document.entity.Document;
import com.bidops.domain.document.enums.DocumentParseStatus;
import com.bidops.domain.document.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, String> {

    /**
     * GET /projects/{projectId}/documents
     * type, parse_status 필터 모두 optional
     */
    @Query("""
            SELECT d FROM Document d
            WHERE d.projectId = :projectId
              AND d.deleted   = false
              AND (:type        IS NULL OR d.type        = :type)
              AND (:parseStatus IS NULL OR d.parseStatus = :parseStatus)
            ORDER BY d.createdAt DESC
            """)
    List<Document> findByProjectId(
            @Param("projectId")   String projectId,
            @Param("type")        DocumentType type,
            @Param("parseStatus") DocumentParseStatus parseStatus);

    /**
     * GET /projects/{projectId}/documents/{documentId}/versions
     * 같은 프로젝트+파일명의 모든 버전 (버전 오름차순)
     */
    @Query("""
            SELECT d FROM Document d
            WHERE d.projectId = :projectId
              AND d.fileName  = :fileName
              AND d.deleted   = false
            ORDER BY d.version ASC
            """)
    List<Document> findVersions(
            @Param("projectId") String projectId,
            @Param("fileName")  String fileName);

    Optional<Document> findByIdAndProjectIdAndDeletedFalse(String id, String projectId);
}
