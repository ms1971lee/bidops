package com.bidops.domain.artifact.entity;

import com.bidops.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "artifact_versions", indexes = {
        @Index(name = "idx_artver_artifact", columnList = "artifact_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ArtifactVersion extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "artifact_id", nullable = false, length = 36)
    private String artifactId;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "file_name", length = 300)
    private String fileName;

    @Column(name = "storage_path", length = 500)
    private String storagePath;

    @Column(name = "version_note", length = 500)
    private String versionNote;

    @Column(name = "uploaded_by", length = 36)
    private String uploadedBy;
}
