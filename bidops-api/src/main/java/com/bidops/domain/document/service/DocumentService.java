package com.bidops.domain.document.service;

import com.bidops.common.response.ListData;
import com.bidops.domain.document.dto.DocumentDto;
import com.bidops.domain.document.dto.DocumentUploadRequest;
import com.bidops.domain.document.enums.DocumentParseStatus;
import com.bidops.domain.document.enums.DocumentType;

import com.bidops.domain.document.dto.SourceExcerptDetailDto;

import java.util.List;

public interface DocumentService {

    /** GET /projects/{projectId}/documents */
    ListData<DocumentDto> listDocuments(String projectId, DocumentType type, DocumentParseStatus parseStatus);

    /** POST /projects/{projectId}/documents */
    DocumentDto uploadDocument(String projectId, DocumentUploadRequest request);

    /** GET /projects/{projectId}/documents/{documentId} */
    DocumentDto getDocument(String projectId, String documentId);

    /** DELETE /projects/{projectId}/documents/{documentId} */
    void deleteDocument(String projectId, String documentId);

    /** GET /projects/{projectId}/documents/{documentId}/versions */
    List<DocumentDto> listVersions(String projectId, String documentId);

    /** PATCH /projects/{projectId}/documents/{documentId}/parse-status (AI worker callback) */
    DocumentDto updateParseStatus(String projectId, String documentId, DocumentParseStatus status);

    /** PATCH /projects/{projectId}/documents/{documentId}/parse-status (AI worker callback, with page count) */
    DocumentDto updateParseStatus(String projectId, String documentId, DocumentParseStatus status, Integer pageCount);

    /** GET /source-excerpts/{id} */
    SourceExcerptDetailDto getSourceExcerpt(String sourceExcerptId);
}
