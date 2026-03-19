package com.bidops.common.storage;

import com.bidops.common.exception.BidOpsException;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * S3 호환 스토리지 기반 StorageService (MinIO Java SDK).
 * MinIO, AWS S3, Railway Object Storage 등에 사용.
 *
 * presigned URL로 프론트에서 직접 PDF를 조회할 수 있다.
 * presigned URL 유효기간: 1시간.
 */
@Slf4j
public class S3StorageService implements StorageService {

    @Value("${bidops.storage.base-url}")
    private String baseUrl;

    @Value("${bidops.storage.bucket}")
    private String bucket;

    @Value("${bidops.storage.access-key:minioadmin}")
    private String accessKey;

    @Value("${bidops.storage.secret-key:minioadmin}")
    private String secretKey;

    @Value("${bidops.storage.presigned-expiry-minutes:60}")
    private int presignedExpiryMinutes;

    private MinioClient minioClient;

    @PostConstruct
    void init() {
        minioClient = MinioClient.builder()
                .endpoint(baseUrl)
                .credentials(accessKey, secretKey)
                .build();

        // 버킷 자동 생성 (없으면)
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("[S3] bucket created: {}", bucket);
            }
            log.info("[S3] connected: {} / {}", baseUrl, bucket);
        } catch (Exception e) {
            log.error("[S3] init failed: {}", e.getMessage());
            throw new RuntimeException("S3 스토리지 초기화 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public String store(String directory, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) originalFilename = "unknown";

        String objectName = directory + "/" + originalFilename;

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(is, file.getSize(), -1)
                    .contentType("application/pdf")
                    .build());

            log.info("[S3] stored: {}/{}", bucket, objectName);
        } catch (Exception e) {
            throw BidOpsException.badRequest("S3 업로드 실패: " + e.getMessage());
        }

        return objectName;
    }

    @Override
    public Resource load(String storagePath) {
        try {
            InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(storagePath)
                    .build());
            return new InputStreamResource(stream);
        } catch (Exception e) {
            throw BidOpsException.notFound("파일");
        }
    }

    @Override
    public String toViewerUrl(String storagePath) {
        try {
            String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(storagePath)
                    .expiry(presignedExpiryMinutes, TimeUnit.MINUTES)
                    .build());
            return url;
        } catch (Exception e) {
            log.warn("[S3] presigned URL 생성 실패, proxy fallback: {}", e.getMessage());
            return "/api/v1/files/" + storagePath;
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(storagePath)
                    .build());
            log.info("[S3] deleted: {}/{}", bucket, storagePath);
        } catch (Exception e) {
            log.warn("[S3] delete failed: {}", e.getMessage());
        }
    }
}
