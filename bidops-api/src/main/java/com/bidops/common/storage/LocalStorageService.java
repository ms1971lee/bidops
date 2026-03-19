package com.bidops.common.storage;

import com.bidops.common.exception.BidOpsException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 로컬 파일시스템 기반 StorageService.
 * bidops.storage.local-dir 설정값 기준으로 파일 저장/조회.
 * StorageConfig에서 provider=local일 때 Bean 등록.
 */
@Slf4j
public class LocalStorageService implements StorageService {

    @Value("${bidops.storage.local-dir:./storage}")
    private String localDir;

    private Path rootPath;

    @PostConstruct
    void init() {
        rootPath = Paths.get(localDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootPath);
            log.info("[Storage] local root: {}", rootPath);
        } catch (IOException e) {
            throw new RuntimeException("스토리지 디렉토리 생성 실패: " + rootPath, e);
        }
    }

    @Override
    public String store(String directory, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) originalFilename = "unknown";

        // 상대 경로: directory/filename
        String storagePath = directory + "/" + originalFilename;
        Path targetPath = rootPath.resolve(storagePath).normalize();

        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("[Storage] stored: {}", storagePath);
        } catch (IOException e) {
            throw BidOpsException.badRequest("파일 저장 실패: " + e.getMessage());
        }

        return storagePath;
    }

    @Override
    public Resource load(String storagePath) {
        Path filePath = rootPath.resolve(storagePath).normalize();
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) {
                throw BidOpsException.notFound("파일");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw BidOpsException.notFound("파일");
        }
    }

    @Override
    public String toViewerUrl(String storagePath) {
        return "/api/v1/files/" + storagePath;
    }

    @Override
    public void delete(String storagePath) {
        Path filePath = rootPath.resolve(storagePath).normalize();
        try {
            if (Files.deleteIfExists(filePath)) {
                log.info("[Storage] deleted: {}", storagePath);
            }
        } catch (IOException e) {
            log.warn("[Storage] delete failed: {}", e.getMessage());
        }
    }
}
