package com.bidops.common.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장소 추상화.
 * MVP: 로컬 파일시스템. 이후 S3 호환 스토리지로 교체 가능.
 */
public interface StorageService {

    /**
     * 파일 저장. 상대 경로(storagePath)를 반환한다.
     */
    String store(String directory, MultipartFile file);

    /**
     * 저장된 파일을 Resource로 반환.
     */
    Resource load(String storagePath);

    /**
     * storagePath → 프론트에서 접근 가능한 URL 반환.
     */
    String toViewerUrl(String storagePath);
}
