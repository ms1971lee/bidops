package com.bidops.domain.project.entity;

import com.bidops.common.entity.BaseEntity;
import com.bidops.domain.project.enums.ProjectStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Project extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "client_name", nullable = false, length = 200)
    private String clientName;

    /** 사업명 (openapi: business_name) */
    @Column(name = "business_name", length = 300)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.DRAFT;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    // ── 변경 메서드 ────────────────────────────────────────────────────────────
    public void update(String name, String clientName, String businessName, String description) {
        if (name != null)         this.name = name;
        if (clientName != null)   this.clientName = clientName;
        if (businessName != null) this.businessName = businessName;
        if (description != null)  this.description = description;
    }

    public void changeStatus(ProjectStatus newStatus) {
        this.status = newStatus;
    }

    public void delete() {
        this.deleted = true;
    }
}
