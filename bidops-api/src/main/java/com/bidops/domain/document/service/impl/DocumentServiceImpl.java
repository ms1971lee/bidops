package com.bidops.domain.document.service.impl;

import com.bidops.common.exception.BidOpsException;
import com.bidops.common.response.ListData;
import com.bidops.common.storage.StorageService;
import com.bidops.domain.document.dto.DocumentDto;
import com.bidops.domain.document.dto.DocumentUploadRequest;
import com.bidops.domain.document.entity.Document;
import com.bidops.domain.document.enums.DocumentParseStatus;
import com.bidops.domain.document.enums.DocumentType;
import com.bidops.domain.document.repository.DocumentRepository;
import com.bidops.domain.document.service.DocumentService;
import com.bidops.domain.project.repository.ProjectRepository;
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
    private final ProjectRepository  projectRepository;
    private final StorageService     storageService;

    @Override
    public ListData<DocumentDto> listDocuments(String projectId, DocumentType type, DocumentParseStatus parseStatus) {
        validateProject(projectId);
        List<DocumentDto> items = documentRepository
                .findByProjectId(projectId, type, parseStatus)
                .stream().map(d -> toDto(d)).toList();
        return new ListData<>(items, items.size());
    }

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    @Override
    @Transactional
    public DocumentDto uploadDocument(String projectId, DocumentUploadRequest request) {
        validateProject(projectId);

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

        return toDto(documentRepository.save(document));
    }

    @Override
    public DocumentDto getDocument(String projectId, String documentId) {
        return toDto(findOrThrow(projectId, documentId));
    }

    @Override
    @Transactional
    public void deleteDocument(String projectId, String documentId) {
        Document doc = findOrThrow(projectId, documentId);
        storageService.delete(doc.getStoragePath());
        doc.delete();
    }

    @Override
    public List<DocumentDto> listVersions(String projectId, String documentId) {
        Document doc = findOrThrow(projectId, documentId);
        return documentRepository
                .findVersions(projectId, doc.getFileName())
                .stream().map(d -> toDto(d)).toList();
    }

    // ── internal ─────────────────────────────────────────────────────────────
    private Document findOrThrow(String projectId, String documentId) {
        return documentRepository.findByIdAndProjectIdAndDeletedFalse(documentId, projectId)
                .orElseThrow(() -> BidOpsException.notFound("문서"));
    }

    private void validateProject(String projectId) {
        projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> BidOpsException.notFound("프로젝트"));
    }

    private DocumentDto toDto(Document d) {
        return DocumentDto.from(d, storageService.toViewerUrl(d.getStoragePath()));
    }
}
