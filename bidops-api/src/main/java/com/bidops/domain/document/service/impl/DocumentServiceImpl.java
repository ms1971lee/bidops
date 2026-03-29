package com.bidops.domain.document.service.impl;

import com.bidops.common.exception.BidOpsException;
import com.bidops.common.response.ListData;
import com.bidops.common.storage.StorageService;
import com.bidops.domain.document.dto.DocumentDto;
import com.bidops.domain.document.dto.DocumentUploadRequest;
import com.bidops.domain.document.dto.SourceExcerptDetailDto;
import com.bidops.domain.document.entity.Document;
import com.bidops.domain.document.entity.SourceExcerpt;
import com.bidops.domain.document.enums.DocumentParseStatus;
import com.bidops.domain.document.enums.DocumentType;
import com.bidops.domain.document.repository.DocumentRepository;
import com.bidops.domain.document.repository.SourceExcerptRepository;
import com.bidops.domain.document.service.DocumentService;
import com.bidops.domain.project.enums.ActivityType;
import com.bidops.domain.project.enums.ProjectPermission;
import com.bidops.domain.project.service.ProjectActivityService;
import com.bidops.domain.project.service.ProjectAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final SourceExcerptRepository sourceExcerptRepository;
    private final ProjectAuthorizationService authorizationService;
    private final StorageService     storageService;
    private final ProjectActivityService activityService;

    @Override
    public ListData<DocumentDto> listDocuments(String projectId, DocumentType type, DocumentParseStatus parseStatus) {
        requirePermission(projectId, ProjectPermission.DOCUMENT_VIEW);
        List<DocumentDto> items = documentRepository
                .findByProjectId(projectId, type, parseStatus)
                .stream().map(d -> toDto(d)).toList();
        return new ListData<>(items, items.size());
    }

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    @Override
    @Transactional
    public DocumentDto uploadDocument(String projectId, DocumentUploadRequest request) {
        requirePermission(projectId, ProjectPermission.DOCUMENT_UPLOAD);

        // MVP: PDF only — HWP/HWPX는 사용자가 PDF 변환 후 업로드
        String filename = request.getFile().getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw BidOpsException.badRequest("PDF 파일만 업로드할 수 있습니다. HWP/HWPX는 PDF로 변환 후 업로드해 주세요.");
        }

        String directory = "projects/" + projectId + "/documents";
        String storagePath = storageService.store(directory, request.getFile());

        // 같은 타입+파일명의 최신 버전 번호 계산
        int nextVersion = documentRepository
                .findVersions(projectId, request.getFile().getOriginalFilename())
                .stream().mapToInt(Document::getVersion).max().orElse(0) + 1;

        Document document = Document.builder()
                .projectId(projectId)
                .type(request.getType())
                .fileName(request.getFile().getOriginalFilename())
                .storagePath(storagePath)
                .version(nextVersion)
                .versionNote(request.getVersionNote())
                .parseStatus(DocumentParseStatus.UPLOADED)
                .build();

        Document saved = documentRepository.save(document);
        activityService.record(projectId, ActivityType.DOCUMENT_UPLOADED,
                "문서 업로드: " + saved.getFileName(),
                com.bidops.auth.SecurityUtils.currentUserId(),
                saved.getId(), "document", null);
        return toDto(saved);
    }

    @Override
    public DocumentDto getDocument(String projectId, String documentId) {
        requirePermission(projectId, ProjectPermission.DOCUMENT_VIEW);
        return toDto(findOrThrow(projectId, documentId));
    }

    @Override
    @Transactional
    public void deleteDocument(String projectId, String documentId) {
        requirePermission(projectId, ProjectPermission.DOCUMENT_DELETE);
        Document doc = findOrThrow(projectId, documentId);
        storageService.delete(doc.getStoragePath());
        doc.delete();
        activityService.record(projectId, ActivityType.DOCUMENT_DELETED,
                "문서 삭제: " + doc.getFileName(),
                com.bidops.auth.SecurityUtils.currentUserId(),
                documentId, "document", null);
    }

    @Override
    public List<DocumentDto> listVersions(String projectId, String documentId) {
        requirePermission(projectId, ProjectPermission.DOCUMENT_VIEW);
        Document doc = findOrThrow(projectId, documentId);
        return documentRepository
                .findVersions(projectId, doc.getFileName())
                .stream().map(d -> toDto(d)).toList();
    }

    @Override
    @Transactional
    public DocumentDto updateParseStatus(String projectId, String documentId, DocumentParseStatus status) {
        return updateParseStatus(projectId, documentId, status, null);
    }

    @Override
    @Transactional
    public DocumentDto updateParseStatus(String projectId, String documentId, DocumentParseStatus status, Integer pageCount) {
        Document doc = findOrThrow(projectId, documentId);
        doc.updateParseStatus(status);
        if (pageCount != null && pageCount > 0) {
            doc.updatePageCount(pageCount);
        }
        return toDto(doc);
    }

    @Override
    public SourceExcerptDetailDto getSourceExcerpt(String sourceExcerptId) {
        SourceExcerpt excerpt = sourceExcerptRepository.findById(sourceExcerptId)
                .orElseThrow(() -> BidOpsException.notFound("원문 발췌"));

        // SourceExcerpt → Document → projectId 역추적 후 권한 검증
        Document doc = documentRepository.findById(excerpt.getDocumentId())
                .orElseThrow(() -> BidOpsException.notFound("문서"));
        requirePermission(doc.getProjectId(), ProjectPermission.DOCUMENT_VIEW);

        return SourceExcerptDetailDto.from(excerpt);
    }

    // ── internal ─────────────────────────────────────────────────────────────
    private Document findOrThrow(String projectId, String documentId) {
        return documentRepository.findByIdAndProjectIdAndDeletedFalse(documentId, projectId)
                .orElseThrow(() -> BidOpsException.notFound("문서"));
    }

    private void requirePermission(String projectId, ProjectPermission permission) {
        authorizationService.requirePermission(projectId, com.bidops.auth.SecurityUtils.currentUserId(), permission);
    }

    private DocumentDto toDto(Document d) {
        return DocumentDto.from(d, storageService.toViewerUrl(d.getStoragePath()));
    }
}
