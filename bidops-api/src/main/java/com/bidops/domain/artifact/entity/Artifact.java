package com.bidops.domain.artifact.entity;

import com.bidops.common.entity.BaseEntity;
import com.bidops.domain.artifact.enums.ArtifactStatus;
import com.bidops.domain.artifact.enums.AssetType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "artifacts", indexes = {
        @Index(name = "idx_artifacts_project", columnList = "project_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Artifact extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 30)
    private AssetType assetType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ArtifactStatus status = ArtifactStatus.DRAFT;

    @Column(length = 500)
    private String description;

    /** 연결된 요구사항 ID (선택) */
    @Column(name = "linked_requirement_id", length = 36)
    private String linkedRequirementId;

    /** 연결된 체크리스트 항목 ID (선택) */
    @Column(name = "linked_checklist_item_id", length = 36)
    private String linkedChecklistItemId;

    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    public void update(String title, AssetType assetType, String description,
                       String linkedRequirementId, String linkedChecklistItemId) {
        if (title != null) this.title = title;
        if (assetType != null) this.assetType = assetType;
        if (description != null) this.description = description;
        this.linkedRequirementId = linkedRequirementId;
        this.linkedChecklistItemId = linkedChecklistItemId;
    }

    public void changeStatus(ArtifactStatus status) {
        this.status = status;
    }

    public void delete() {
        this.deleted = true;
    }
}
