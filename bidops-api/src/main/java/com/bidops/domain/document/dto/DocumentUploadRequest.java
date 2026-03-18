package com.bidops.domain.document.dto;

import com.bidops.domain.document.enums.DocumentType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * POST /projects/{projectId}/documents
 * multipart/form-data: file + type + version_note
 */
@Getter
@Setter
@NoArgsConstructor
public class DocumentUploadRequest {

    @NotNull(message = "file은 필수입니다.")
    private MultipartFile file;

    @NotNull(message = "type은 필수입니다.")
    private DocumentType type;

    private String versionNote;
}
