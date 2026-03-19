package com.bidops.common.storage;

import com.bidops.common.exception.BidOpsException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * S3 호환 스토리지 기반 StorageService.
 * MinIO, AWS S3, Railway Object Storage 등에 사용.
 *
 * MVP에서는 presigned URL 없이 public-read 버킷 기준으로 구현.
 * bidops.storage.base-url + bucket + storagePath 로 URL 생성.
 *
 * TODO: AWS SDK v2 또는 MinIO Client로 전환 시 이 클래스 교체
 */
@Slf4j
public class S3StorageService implements StorageService {

    @Value("${bidops.storage.base-url}")
    private String baseUrl;

    @Value("${bidops.storage.bucket}")
    private String bucket;

    @Value("${bidops.storage.access-key:}")
    private String accessKey;

    @Value("${bidops.storage.secret-key:}")
    private String secretKey;

    private String bucketUrl;

    @PostConstruct
    void init() {
        bucketUrl = baseUrl.replaceAll("/$", "") + "/" + bucket;
        log.info("[Storage] S3 bucket: {}", bucketUrl);
    }

    @Override
    public String store(String directory, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) originalFilename = "unknown";

        String storagePath = directory + "/" + originalFilename;
        String uploadUrl = bucketUrl + "/" + storagePath;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                    .header("Content-Type", "application/pdf")
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                throw BidOpsException.badRequest("S3 업로드 실패: HTTP " + response.statusCode());
            }

            log.info("[Storage] S3 stored: {}", storagePath);
        } catch (IOException | InterruptedException e) {
            throw BidOpsException.badRequest("S3 업로드 실패: " + e.getMessage());
        }

        return storagePath;
    }

    @Override
    public Resource load(String storagePath) {
        String fileUrl = bucketUrl + "/" + storagePath;
        try {
            Resource resource = new UrlResource(fileUrl);
            if (!resource.exists()) {
                throw BidOpsException.notFound("파일");
            }
            return resource;
        } catch (Exception e) {
            throw BidOpsException.notFound("파일");
        }
    }

    @Override
    public String toViewerUrl(String storagePath) {
        // S3: 직접 버킷 URL 반환 (public-read) 또는 프록시 경로
        // 프록시 경로 유지하면 CORS 문제 없음
        return "/api/v1/files/" + storagePath;
    }
}
