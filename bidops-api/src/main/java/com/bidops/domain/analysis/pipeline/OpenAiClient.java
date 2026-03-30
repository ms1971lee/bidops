package com.bidops.domain.analysis.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completion API 클라이언트.
 *
 * 재시도 정책:
 * - 이 클라이언트는 1회 호출만 수행한다 (재시도 없음).
 * - 재시도는 AnalysisJobWorker 레벨에서 job 상태 기반으로 수행한다.
 * - 에러 발생 시 OpenAiException으로 분류하여 retryable 여부를 표시한다.
 *
 * 책임 분리:
 *   OpenAiClient  → 1회 호출 + 에러 분류 + OpenAiException throw
 *   Worker        → job.canRetry() + retryable 체크 → PENDING 복귀 또는 FAILED
 */
@Slf4j
@Component
public class OpenAiClient {

    @Value("${bidops.ai.openai-api-key:}")
    private String apiKey;

    @Value("${bidops.ai.openai-model:gpt-4o}")
    private String model;

    @Value("${bidops.ai.openai-base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${bidops.ai.connect-timeout-seconds:30}")
    private int connectTimeoutSeconds;

    @Value("${bidops.ai.read-timeout-seconds:120}")
    private int readTimeoutSeconds;

    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    public OpenAiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                    .build();
        }
        return httpClient;
    }

    // 테스트용
    void setHttpClient(HttpClient client) {
        this.httpClient = client;
    }

    /**
     * Chat completion 1회 호출. JSON 응답의 content 문자열을 반환.
     * 재시도는 수행하지 않는다 — 호출자(Worker)가 책임진다.
     *
     * @throws OpenAiException 분류된 에러 코드 포함 (retryable 여부 표시)
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new OpenAiException(OpenAiErrorCode.OPENAI_AUTH_ERROR, "API Key 미설정");
        }

        String requestBody = buildRequestBody(systemPrompt, userPrompt);
        String url = (baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1") + "/chat/completions";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(readTimeoutSeconds))
                .build();

        HttpResponse<String> response;
        try {
            response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new OpenAiException(OpenAiErrorCode.OPENAI_TIMEOUT,
                    "read timeout after " + readTimeoutSeconds + "s", e);
        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new OpenAiException(OpenAiErrorCode.OPENAI_CONNECTION_ERROR, msg, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpenAiException(OpenAiErrorCode.OPENAI_CONNECTION_ERROR, "interrupted", e);
        }

        int status = response.statusCode();
        String bodySnippet = response.body().length() > 500
                ? response.body().substring(0, 500) : response.body();

        log.debug("[OpenAI] HTTP {} ({}bytes)", status, response.body().length());

        if (status == 200) {
            return parseContent(response.body());
        }

        // HTTP 에러 분류
        if (status == 429) {
            throw new OpenAiException(OpenAiErrorCode.OPENAI_RATE_LIMIT, status,
                    "HTTP 429: " + bodySnippet);
        }
        if (status >= 500) {
            throw new OpenAiException(OpenAiErrorCode.OPENAI_SERVER_ERROR, status,
                    "HTTP " + status + ": " + bodySnippet);
        }
        if (status == 401 || status == 403) {
            throw new OpenAiException(OpenAiErrorCode.OPENAI_AUTH_ERROR, status,
                    "HTTP " + status + ": " + bodySnippet);
        }
        // 400 계열
        throw new OpenAiException(OpenAiErrorCode.OPENAI_BAD_REQUEST, status,
                "HTTP " + status + ": " + bodySnippet);
    }

    private String parseContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String finishReason = root.path("choices").path(0).path("finish_reason").asText();
            int totalTokens = root.path("usage").path("total_tokens").asInt();
            log.info("[OpenAI] finish_reason={} total_tokens={} model={}", finishReason, totalTokens, model);

            if ("length".equals(finishReason)) {
                log.warn("[OpenAI] 응답이 토큰 제한으로 잘렸습니다!");
            }

            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (content == null || content.isBlank()) {
                throw new OpenAiException(OpenAiErrorCode.OPENAI_PARSE_ERROR,
                        "응답 content 비어있음. finish_reason=" + finishReason);
            }
            return content;
        } catch (OpenAiException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenAiException(OpenAiErrorCode.OPENAI_PARSE_ERROR,
                    "응답 JSON 파싱 실패: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.1,
                "max_tokens", 16000,
                "response_format", Map.of("type", "json_object")
        );
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new OpenAiException(OpenAiErrorCode.OPENAI_BAD_REQUEST,
                    "요청 JSON 생성 실패: " + e.getMessage(), e);
        }
    }
}
