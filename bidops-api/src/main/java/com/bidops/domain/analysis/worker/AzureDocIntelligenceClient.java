package com.bidops.domain.analysis.worker;

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
import java.time.Duration;
import java.util.*;

/**
 * Azure Document Intelligence Layout API 클라이언트.
 *
 * 스캔 PDF/이미지 문서의 OCR + 레이아웃 분석.
 * 단락별 bbox, type(paragraph/table/header/list) 추출.
 *
 * 설정:
 *   bidops.azure-di.endpoint = https://xxx.cognitiveservices.azure.com
 *   bidops.azure-di.api-key = xxx
 *   bidops.azure-di.enabled = true
 */
@Slf4j
@Component
public class AzureDocIntelligenceClient {

    @Value("${bidops.azure-di.endpoint:}")
    private String endpoint;

    @Value("${bidops.azure-di.api-key:}")
    private String apiKey;

    @Value("${bidops.azure-di.enabled:false}")
    private boolean enabled;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AzureDocIntelligenceClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public boolean isAvailable() {
        return enabled && endpoint != null && !endpoint.isBlank()
                && apiKey != null && !apiKey.isBlank();
    }

    public DocumentExtractionResult analyze(byte[] pdfBytes) {
        if (!isAvailable()) {
            throw new RuntimeException("Azure Document Intelligence가 설정되지 않았습니다.");
        }
        log.info("[AzureDI] 분석 시작: {}bytes", pdfBytes.length);
        try {
            String operationUrl = submitAnalysis(pdfBytes);
            JsonNode result = pollResult(operationUrl);
            return parseLayoutResult(result);
        } catch (Exception e) {
            throw new RuntimeException("Azure DI 분석 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Azure DI JSON 응답을 파싱하여 DocumentExtractionResult 반환.
     * 테스트에서 직접 호출 가능하도록 패키지 접근 허용.
     */
    DocumentExtractionResult parseLayoutResult(JsonNode analyzeResult) {
        List<DocumentExtractionResult.PageText> pages = new ArrayList<>();
        List<DocumentExtractionResult.Paragraph> allParagraphs = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();

        int tableCount = 0;
        int headerCount = 0;
        int listCount = 0;
        int emptyPageCount = 0;

        // ── 페이지별 lines 추출 ──────────────────────────────────────────
        JsonNode pagesNode = analyzeResult.path("pages");
        int totalPages = pagesNode.size();

        for (JsonNode page : pagesNode) {
            int pageNo = page.path("pageNumber").asInt();
            float pageWidth = page.path("width").floatValue();
            float pageHeight = page.path("height").floatValue();

            StringBuilder pageText = new StringBuilder();
            List<DocumentExtractionResult.Paragraph> pageParagraphs = new ArrayList<>();

            for (JsonNode line : page.path("lines")) {
                String content = line.path("content").asText();
                pageText.append(content).append("\n");

                // line-level bbox → paragraph로 변환
                String bboxJson = extractBbox(line.path("polygon"), pageWidth, pageHeight);

                pageParagraphs.add(DocumentExtractionResult.Paragraph.builder()
                        .pageNo(pageNo)
                        .text(content)
                        .type("PARAGRAPH")
                        .anchorLabel(null)
                        .bboxJson(bboxJson)
                        .build());
            }

            String text = pageText.toString().trim();
            boolean isEmpty = text.length() < 10;
            if (isEmpty) emptyPageCount++;

            pages.add(DocumentExtractionResult.PageText.builder()
                    .pageNo(pageNo)
                    .text(text)
                    .charCount(text.length())
                    .hasTable(false) // 표는 별도 처리
                    .isEmpty(isEmpty)
                    .paragraphs(pageParagraphs)
                    .build());

            allParagraphs.addAll(pageParagraphs);
            fullText.append("\n\n--- [페이지 ").append(pageNo).append("] ---\n\n");
            fullText.append(text);
        }

        // ── paragraphs 섹션 (role 기반 유형 분류) ──────────────────────────
        JsonNode paragraphsNode = analyzeResult.path("paragraphs");
        if (paragraphsNode.isArray()) {
            for (JsonNode para : paragraphsNode) {
                String role = para.path("role").asText("");
                String content = para.path("content").asText("");
                int paraPageNo = para.path("boundingRegions").path(0).path("pageNumber").asInt(0);

                String type = switch (role) {
                    case "title", "sectionHeading" -> "HEADER";
                    case "footnote" -> "FOOTNOTE";
                    case "pageHeader", "pageFooter" -> "HEADER";
                    default -> "PARAGRAPH";
                };

                if ("HEADER".equals(type)) headerCount++;

                // 조항 번호 감지
                String anchorLabel = null;
                var clauses = DocumentTextExtractor.extractClauses(content);
                if (!clauses.isEmpty()) {
                    anchorLabel = clauses.get(0).clauseId();
                }

                String bboxJson = extractBboxFromRegions(para.path("boundingRegions"));

                allParagraphs.add(DocumentExtractionResult.Paragraph.builder()
                        .pageNo(paraPageNo)
                        .text(content)
                        .type(type)
                        .anchorLabel(anchorLabel)
                        .bboxJson(bboxJson)
                        .build());
            }
        }

        // ── 표 데이터 ────────────────────────────────────────────────────
        JsonNode tables = analyzeResult.path("tables");
        if (tables.isArray()) {
            for (JsonNode table : tables) {
                tableCount++;
                int tablePageNo = table.path("boundingRegions").path(0).path("pageNumber").asInt(0);
                int rowCount = table.path("rowCount").asInt();
                int colCount = table.path("columnCount").asInt();

                StringBuilder tableText = new StringBuilder();
                tableText.append("[표 ").append(rowCount).append("x").append(colCount)
                        .append(" - 페이지 ").append(tablePageNo).append("]\n");

                // 셀 데이터를 행/열 순서로 조합
                Map<String, String> cellMap = new TreeMap<>();
                for (JsonNode cell : table.path("cells")) {
                    int row = cell.path("rowIndex").asInt();
                    int col = cell.path("columnIndex").asInt();
                    String content = cell.path("content").asText("");
                    cellMap.put(String.format("%04d_%04d", row, col), content);
                }

                int prevRow = -1;
                for (var entry : cellMap.entrySet()) {
                    int row = Integer.parseInt(entry.getKey().split("_")[0]);
                    if (row != prevRow) {
                        if (prevRow >= 0) tableText.append("\n");
                        prevRow = row;
                    } else {
                        tableText.append("\t");
                    }
                    tableText.append(entry.getValue());
                }

                String tableStr = tableText.toString();
                fullText.append("\n").append(tableStr).append("\n");

                // 표 → HEADER 페이지에 hasTable 플래그 설정
                for (int pi = 0; pi < pages.size(); pi++) {
                    if (pages.get(pi).getPageNo() == tablePageNo && !pages.get(pi).isHasTable()) {
                        var oldPage = pages.get(pi);
                        pages.set(pi, DocumentExtractionResult.PageText.builder()
                                .pageNo(oldPage.getPageNo())
                                .text(oldPage.getText())
                                .charCount(oldPage.getCharCount())
                                .hasTable(true)
                                .isEmpty(oldPage.isEmpty())
                                .paragraphs(oldPage.getParagraphs())
                                .build());
                    }
                }

                String tableBbox = extractBboxFromRegions(table.path("boundingRegions"));
                allParagraphs.add(DocumentExtractionResult.Paragraph.builder()
                        .pageNo(tablePageNo)
                        .text(tableStr)
                        .type("TABLE")
                        .anchorLabel(null)
                        .bboxJson(tableBbox)
                        .build());
            }
        }

        var stats = DocumentExtractionResult.ExtractionStats.builder()
                .totalChars(fullText.length())
                .totalParagraphs(allParagraphs.size())
                .tableCount(tableCount)
                .emptyPageCount(emptyPageCount)
                .headerCount(headerCount)
                .listCount(listCount)
                .build();

        return DocumentExtractionResult.builder()
                .fullText(fullText.toString().trim())
                .totalPages(totalPages)
                .pages(pages)
                .extractionMethod("AZURE_DI")
                .stats(stats)
                .build();
    }

    // ── bbox 변환 ────────────────────────────────────────────────────────

    /** Azure DI polygon [x1,y1,x2,y2,...x4,y4] → 퍼센트 기반 bbox JSON */
    private String extractBbox(JsonNode polygon, float pageWidth, float pageHeight) {
        if (polygon == null || !polygon.isArray() || polygon.size() < 8) return null;
        if (pageWidth <= 0 || pageHeight <= 0) return null;

        float x1 = polygon.get(0).floatValue();
        float y1 = polygon.get(1).floatValue();
        float x3 = polygon.get(4).floatValue();
        float y3 = polygon.get(5).floatValue();

        float x = (x1 / pageWidth) * 100;
        float y = (y1 / pageHeight) * 100;
        float w = ((x3 - x1) / pageWidth) * 100;
        float h = ((y3 - y1) / pageHeight) * 100;

        return String.format("{\"x\":%.1f,\"y\":%.1f,\"w\":%.1f,\"h\":%.1f}", x, y, w, h);
    }

    /** boundingRegions[0].polygon → bbox JSON */
    private String extractBboxFromRegions(JsonNode boundingRegions) {
        if (boundingRegions == null || !boundingRegions.isArray() || boundingRegions.isEmpty()) return null;
        JsonNode first = boundingRegions.get(0);
        // boundingRegions에는 pageWidth/Height가 없으므로 default 8.5x11 inch 사용
        return extractBbox(first.path("polygon"), 8.5f, 11f);
    }

    // ── HTTP ─────────────────────────────────────────────────────────────

    private String submitAnalysis(byte[] pdfBytes) throws IOException, InterruptedException {
        String url = endpoint + "/documentintelligence/documentModels/prebuilt-layout:analyze"
                + "?api-version=2024-11-30";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .header("Content-Type", "application/pdf")
                .POST(HttpRequest.BodyPublishers.ofByteArray(pdfBytes))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 202) {
            throw new RuntimeException("Azure DI 요청 실패 (HTTP " + response.statusCode() + ")");
        }

        return response.headers().firstValue("Operation-Location")
                .orElseThrow(() -> new RuntimeException("Operation-Location 헤더 없음"));
    }

    private JsonNode pollResult(String operationUrl) throws IOException, InterruptedException {
        for (int i = 0; i < 60; i++) {
            Thread.sleep(5000);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(operationUrl))
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .GET().timeout(Duration.ofSeconds(30)).build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            String status = root.path("status").asText();

            if ("succeeded".equals(status)) return root.path("analyzeResult");
            if ("failed".equals(status)) {
                throw new RuntimeException("Azure DI 분석 실패: " + root.path("error"));
            }
        }
        throw new RuntimeException("Azure DI 분석 타임아웃 (5분 초과)");
    }
}
