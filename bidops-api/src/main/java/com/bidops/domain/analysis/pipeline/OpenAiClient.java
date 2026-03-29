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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completion API 클라이언트.
 * 파이프라인 각 단계에서 공유.
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

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Chat completion 호출. JSON 응답의 content 문자열을 반환.
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("OpenAI API Key가 설정되지 않았습니다.");
        }

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

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new RuntimeException("요청 JSON 생성 실패", e);
        }

        String url = (baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1") + "/chat/completions";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofMinutes(5))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("OpenAI API 통신 오류: " + e.getMessage(), e);
        }

        if (response.statusCode() != 200) {
            String bodySnippet = response.body().length() > 500 ? response.body().substring(0, 500) : response.body();
            throw new RuntimeException("OpenAI API 오류 (HTTP " + response.statusCode() + "): " + bodySnippet);
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            String finishReason = root.path("choices").path(0).path("finish_reason").asText();
            int totalTokens = root.path("usage").path("total_tokens").asInt();
            log.info("[OpenAI] finish_reason={} total_tokens={} model={}", finishReason, totalTokens, model);

            if ("length".equals(finishReason)) {
                log.warn("[OpenAI] 응답이 토큰 제한으로 잘렸습니다!");
            }

            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (IOException e) {
            throw new RuntimeException("OpenAI 응답 파싱 실패", e);
        }
    }
}
