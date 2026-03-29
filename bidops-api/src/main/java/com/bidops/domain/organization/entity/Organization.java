package com.bidops.domain.organization.entity;

import com.bidops.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "organizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Organization extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "business_number", length = 20)
    private String businessNumber;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";
}
