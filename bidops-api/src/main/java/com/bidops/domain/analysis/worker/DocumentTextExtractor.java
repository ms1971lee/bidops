package com.bidops.domain.analysis.worker;

import com.bidops.common.exception.BidOpsException;
import com.bidops.common.storage.StorageService;
import com.bidops.domain.document.entity.Document;
import com.bidops.domain.document.enums.DocumentParseStatus;
import com.bidops.domain.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDFBox 기반 문서 텍스트 추출기.
 *
 * - 페이지별 텍스트 추출 (page_no 정확도 보장)
 * - 표/목록 구조 감지
 * - 스캔 PDF 감지 (빈 페이지 경고)
 * - 조항 번호 자동 감지 (anchor 품질 향상)
 * - 파싱 상태 관리 (PARSING → PARSED/FAILED)
 *
 * Azure DI가 설정되어 있고 스캔 PDF가 감지되면 자동으로 Azure DI로 폴백한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentTextExtractor {

    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final AzureDocIntelligenceClient azureDiClient;

    /** 조항 번호 패턴 */
    private static final Pattern CLAUSE_PATTERN = Pattern.compile(
            "^\\s*(제?\\d+[조장절항]|\\d+\\.(?:\\d+\\.?)*|[가-힣]\\.|\\([가-힣0-9]+\\))\\s*(.*)$",
            Pattern.MULTILINE
    );

    /** 표 감지 패턴 */
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            ".*[\\t|]{2,}.*|.*\\s{3,}\\S+\\s{3,}.*"
    );

    /**
     * 단순 텍스트 추출 (기존 호출자 호환).
     */
    public String extract(String documentId) {
        return extractStructured(documentId).getFullText();
    }

    /**
     * 구조화된 추출 결과 반환.
     * 페이지별 텍스트, 표 감지, 빈 페이지 감지 포함.
     */
    public DocumentExtractionResult extractStructured(String documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> BidOpsException.notFound("문서"));

        doc.updateParseStatus(DocumentParseStatus.PARSING);
        documentRepository.save(doc);

        try {
            Resource resource = storageService.load(doc.getStoragePath());
            DocumentExtractionResult result = extractFromPdf(resource);

            // 스캔 PDF 감지 → Azure DI 폴백
            long emptyPages = result.getPages().stream()
                    .filter(DocumentExtractionResult.PageText::isEmpty).count();
            boolean isScannedPdf = result.getTotalPages() > 0
                    && emptyPages > result.getTotalPages() / 2;

            if (isScannedPdf && azureDiClient.isAvailable()) {
                log.info("[TextExtractor] 스캔 PDF 감지 → Azure Document Intelligence 폴백");
                try (InputStream fallbackIs = resource.getInputStream()) {
                    result = azureDiClient.analyze(fallbackIs.readAllBytes());
                }
            } else if (isScannedPdf) {
                log.warn("[TextExtractor] 스캔 PDF 감지, Azure DI 미설정. 텍스트 품질이 낮을 수 있음.");
            }

            doc.updateParseStatus(DocumentParseStatus.PARSED);
            doc.updatePageCount(result.getTotalPages());
            documentRepository.save(doc);

            log.info("[TextExtractor] 추출 완료: documentId={} pages={} chars={} method={}",
                    documentId, result.getTotalPages(), result.getFullText().length(),
                    result.getExtractionMethod());
            return result;
        } catch (Exception e) {
            doc.updateParseStatus(DocumentParseStatus.FAILED);
            documentRepository.save(doc);
            throw new RuntimeException("문서 텍스트 추출 실패: " + e.getMessage(), e);
        }
    }

    // ── PDFBox 추출 ──────────────────────────────────────────────────────

    private DocumentExtractionResult extractFromPdf(Resource resource) {
        try (InputStream is = resource.getInputStream();
             PDDocument document = Loader.loadPDF(is.readAllBytes())) {

            int totalPages = document.getNumberOfPages();
            List<DocumentExtractionResult.PageText> pages = new ArrayList<>();
            StringBuilder fullText = new StringBuilder();
            int totalChars = 0;
            int totalParagraphs = 0;
            int tableCount = 0;
            int emptyPageCount = 0;
            int headerCount = 0;

            PDFTextStripper stripper = new PDFTextStripper();

            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document).trim();

                boolean hasTable = detectTable(pageText);
                boolean isEmpty = pageText.length() < 10;
                if (isEmpty) emptyPageCount++;
                if (hasTable) tableCount++;

                // 단락 분리 + 조항 감지
                List<DocumentExtractionResult.Paragraph> paragraphs = splitParagraphs(pageText, i);
                totalParagraphs += paragraphs.size();
                headerCount += paragraphs.stream()
                        .filter(p -> "HEADER".equals(p.getType())).count();

                totalChars += pageText.length();

                pages.add(DocumentExtractionResult.PageText.builder()
                        .pageNo(i)
                        .text(pageText)
                        .charCount(pageText.length())
                        .hasTable(hasTable)
                        .isEmpty(isEmpty)
                        .paragraphs(paragraphs)
                        .build());

                fullText.append("\n\n--- [페이지 ").append(i).append("] ---\n\n");
                fullText.append(pageText);
            }

            var stats = DocumentExtractionResult.ExtractionStats.builder()
                    .totalChars(totalChars)
                    .totalParagraphs(totalParagraphs)
                    .tableCount(tableCount)
                    .emptyPageCount(emptyPageCount)
                    .headerCount(headerCount)
                    .listCount(0)
                    .build();

            return DocumentExtractionResult.builder()
                    .fullText(fullText.toString().trim())
                    .totalPages(totalPages)
                    .pages(pages)
                    .extractionMethod("PDFBOX")
                    .stats(stats)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("PDFBox 텍스트 추출 실패: " + e.getMessage(), e);
        }
    }

    /** 페이지 텍스트를 단락으로 분리하고 조항/유형을 감지한다. */
    private List<DocumentExtractionResult.Paragraph> splitParagraphs(String pageText, int pageNo) {
        List<DocumentExtractionResult.Paragraph> paragraphs = new ArrayList<>();
        if (pageText == null || pageText.isBlank()) return paragraphs;

        // 빈 줄로 단락 분리
        String[] blocks = pageText.split("\\n\\s*\\n");
        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) continue;

            // 유형 판별
            String type = "PARAGRAPH";
            String anchorLabel = null;

            var clauses = extractClauses(trimmed);
            if (!clauses.isEmpty()) {
                anchorLabel = clauses.get(0).clauseId();
                // 첫 줄만 조항 제목이면 HEADER
                if (trimmed.split("\n").length <= 2) {
                    type = "HEADER";
                }
            }

            if (detectTable(trimmed)) {
                type = "TABLE";
            }

            paragraphs.add(DocumentExtractionResult.Paragraph.builder()
                    .pageNo(pageNo)
                    .text(trimmed)
                    .type(type)
                    .anchorLabel(anchorLabel)
                    .bboxJson(null) // PDFBox에서는 bbox 미제공
                    .build());
        }
        return paragraphs;
    }

    private boolean detectTable(String pageText) {
        if (pageText == null || pageText.isBlank()) return false;
        String[] lines = pageText.split("\n");
        int tableLineCount = 0;
        for (String line : lines) {
            if (TABLE_PATTERN.matcher(line).matches()) tableLineCount++;
        }
        return tableLineCount >= 3;
    }

    /**
     * 텍스트에서 조항 번호를 추출. SourceExcerpt.anchorLabel 생성에 사용.
     */
    public static List<ClauseInfo> extractClauses(String text) {
        List<ClauseInfo> clauses = new ArrayList<>();
        Matcher matcher = CLAUSE_PATTERN.matcher(text);
        while (matcher.find()) {
            clauses.add(new ClauseInfo(
                    matcher.group(1).trim(), matcher.group(2).trim(), matcher.start()));
        }
        return clauses;
    }

    public record ClauseInfo(String clauseId, String title, int position) {}
}
