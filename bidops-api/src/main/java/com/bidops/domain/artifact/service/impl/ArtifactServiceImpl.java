package com.bidops.domain.artifact.service.impl;

import com.bidops.auth.SecurityUtils;
import com.bidops.common.exception.BidOpsException;
import com.bidops.common.storage.StorageService;
import com.bidops.domain.artifact.dto.*;
import com.bidops.domain.artifact.entity.Artifact;
import com.bidops.domain.artifact.entity.ArtifactVersion;
import com.bidops.domain.artifact.enums.ArtifactStatus;
import com.bidops.domain.artifact.repository.ArtifactRepository;
import com.bidops.domain.artifact.repository.ArtifactVersionRepository;
import com.bidops.domain.project.enums.ActivityType;
import com.bidops.domain.project.enums.ProjectPermission;
import com.bidops.domain.project.service.ProjectActivityService;
import com.bidops.domain.project.service.ProjectAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtifactServiceImpl {

    private final ArtifactRepository artifactRepository;
    private final ArtifactVersionRepository versionRepository;
    private final ProjectAuthorizationService authorizationService;
    private final ProjectActivityService activityService;
    private final StorageService storageService;

    public List<ArtifactDto> list(String projectId) {
        requirePermission(projectId, ProjectPermission.PROJECT_VIEW);
        return artifactRepository.findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(projectId)
                .stream().map(ArtifactDto::from).toList();
    }

    public ArtifactDto get(String projectId, String artifactId) {
        requirePermission(projectId, ProjectPermission.PROJECT_VIEW);
        return ArtifactDto.from(findOrThrow(projectId, artifactId));
    }

    @Transactional
    public ArtifactDto create(String projectId, ArtifactCreateRequest request) {
        requirePermission(projectId, ProjectPermission.CHECKLIST_EDIT);
        Artifact artifact = Artifact.builder()
                .projectId(projectId)
                .title(request.getTitle())
                .assetType(request.getAssetType())
                .description(request.getDescription())
                .linkedRequirementId(request.getLinkedRequirementId())
                .linkedChecklistItemId(request.getLinkedChecklistItemId())
                .build();
        Artifact saved = artifactRepository.save(artifact);
        activityService.record(projectId, ActivityType.ARTIFACT_CREATED,
                "산출물 생성: " + saved.getTitle(),
                SecurityUtils.currentUserId(), saved.getId(), "artifact", null);
        return ArtifactDto.from(saved);
    }

    @Transactional
    public ArtifactDto update(String projectId, String artifactId, ArtifactUpdateRequest request) {
        requirePermission(projectId, ProjectPermission.CHECKLIST_EDIT);
        Artifact artifact = findOrThrow(projectId, artifactId);
        artifact.update(request.getTitle(), request.getAssetType(), request.getDescription(),
                request.getLinkedRequirementId(), request.getLinkedChecklistItemId());
        activityService.record(projectId, ActivityType.ARTIFACT_UPDATED,
                "산출물 수정: " + artifact.getTitle(),
                SecurityUtils.currentUserId(), artifactId, "artifact", null);
        return ArtifactDto.from(artifact);
    }

    @Transactional
    public void delete(String projectId, String artifactId) {
        requirePermission(projectId, ProjectPermission.CHECKLIST_EDIT);
        Artifact artifact = findOrThrow(projectId, artifactId);
        artifact.delete();
        activityService.record(projectId, ActivityType.ARTIFACT_DELETED,
                "산출물 삭제: " + artifact.getTitle(),
                SecurityUtils.currentUserId(), artifactId, "artifact", null);
    }

    @Transactional
    public ArtifactDto changeStatus(String projectId, String artifactId, ArtifactStatus status) {
        requirePermission(projectId, ProjectPermission.CHECKLIST_EDIT);
        Artifact artifact = findOrThrow(projectId, artifactId);
        String before = artifact.getStatus().name();
        artifact.changeStatus(status);
        activityService.record(projectId, ActivityType.ARTIFACT_STATUS_CHANGED,
                "산출물 상태: " + artifact.getTitle() + " " + before + " → " + status,
                SecurityUtils.currentUserId(), artifactId, "artifact", before + " → " + status);
        return ArtifactDto.from(artifact);
    }

    @Transactional
    public ArtifactVersionDto uploadVersion(String projectId, String artifactId,
                                             MultipartFile file, String versionNote) {
        requirePermission(projectId, ProjectPermission.CHECKLIST_EDIT);
        Artifact artifact = findOrThrow(projectId, artifactId);
        String directory = "projects/" + projectId + "/artifacts/" + artifactId;
        String storagePath = storageService.store(directory, file);
        int nextVersion = (int) versionRepository.countByArtifactId(artifactId) + 1;

        ArtifactVersion version = ArtifactVersion.builder()
                .artifactId(artifactId).version(nextVersion)
                .fileName(file.getOriginalFilename()).storagePath(storagePath)
                .versionNote(versionNote).uploadedBy(SecurityUtils.currentUserId())
                .build();
        ArtifactVersion saved = versionRepository.save(version);
        activityService.record(projectId, ActivityType.ARTIFACT_VERSION_UPLOADED,
                "산출물 버전: " + artifact.getTitle() + " v" + nextVersion,
                SecurityUtils.currentUserId(), artifactId, "artifact", file.getOriginalFilename());
        return ArtifactVersionDto.from(saved, storageService.toViewerUrl(storagePath));
    }

    public List<ArtifactVersionDto> listVersions(String projectId, String artifactId) {
        requirePermission(projectId, ProjectPermission.PROJECT_VIEW);
        findOrThrow(projectId, artifactId);
        return versionRepository.findByArtifactIdOrderByVersionDesc(artifactId).stream()
                .map(v -> ArtifactVersionDto.from(v, storageService.toViewerUrl(v.getStoragePath())))
                .toList();
    }

    private Artifact findOrThrow(String projectId, String artifactId) {
        return artifactRepository.findByIdAndProjectIdAndDeletedFalse(artifactId, projectId)
                .orElseThrow(() -> BidOpsException.notFound("산출물"));
    }

    private void requirePermission(String projectId, ProjectPermission permission) {
        authorizationService.requirePermission(projectId, SecurityUtils.currentUserId(), permission);
    }
}
