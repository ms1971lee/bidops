package com.bidops.domain.organization.repository;

import com.bidops.domain.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, String> {

    Optional<Organization> findByName(String name);

    boolean existsByName(String name);
}
