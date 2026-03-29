package com.bidops.domain.document.entity;

import com.bidops.common.entity.BaseEntity;
import com.bidops.domain.document.enums.DocumentParseStatus;
import com.bidops.domain.document.enums.DocumentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Document extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentType type;

    @Column(name = "file_name", nullable = false, length = 300)
    private String fileName;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    /** 같은 프로젝트+타입 내에서의 버전 번호 */
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "version_note", length = 500)
    private String versionNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 20)
    @Builder.Default
    private DocumentParseStatus parseStatus = DocumentParseStatus.UPLOADED;

    @Column(name = "uploaded_by", length = 36)
    private String uploadedBy;

    /** 문서 전체 페이지 수 (파싱 완료 후 설정) */
    @Column(name = "page_count")
    private Integer pageCount;

    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    // ── 변경 메서드 ────────────────────────────────────────────────────────────
    public void updateParseStatus(DocumentParseStatus status) {
        this.parseStatus = status;
    }

    public void updatePageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public void delete() {
        this.deleted = true;
    }
}
