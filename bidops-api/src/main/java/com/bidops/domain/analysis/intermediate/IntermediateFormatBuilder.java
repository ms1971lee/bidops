package com.bidops.domain.analysis.intermediate;

import com.bidops.domain.analysis.worker.DocumentExtractionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF 추출 결과(DocumentExtractionResult)를 중간 분석 포맷(IntermediateFormat)으로 변환.
 *
 * 핵심:
 * 1. 페이지별 블록 생성 + block_id 부여
 * 2. 요구사항 번호 패턴 감지 → catalog 생성
 * 3. 의무 표현 감지 → 요구사항 후보 마킹
 * 4. normalized.md 생성
 */
@Slf4j
@Component
public class IntermediateFormatBuilder {

    private static final Pattern REQ_NO_PATTERN = Pattern.compile(
            "\\b(MAR|DAR|MHR|SER|QUR|COR|PMR|PSR|NFR|SFR|SEC|INF|PER|EVL|SCH|DEL|TRN|MNT|LGL)[-\\s]?(\\d{1,3})\\b",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> OBLIGATION_SIGNALS = List.of(
            "하여야 한다", "해야 한다", "하여야한다", "필수", "반드시",
            "제출", "준수", "이상", "이내", "이하", "충족", "보장",
            "제시하여야", "확보하여야", "구축하여야", "수행하여야");

    public IntermediateFormat build(DocumentExtractionResult extraction,
                                    String documentId, String projectId,
                                    String title, String sourceFileName) {

        List<IntermediateFormat.Block> allBlocks = new ArrayList<>();
        List<IntermediateFormat.CatalogItem> catalog = new ArrayList<>();
        Map<String, Integer> expectedBreakdown = new LinkedHashMap<>();
        Set<String> seenReqNos = new LinkedHashSet<>();
        StringBuilder md = new StringBuilder();

        // 메타 헤더
        md.append("---\n");
        md.append("document_id: ").append(documentId).append("\n");
        md.append("project_id: ").append(projectId).append("\n");
        md.append("title: ").append(title != null ? title : "").append("\n");
        md.append("page_count: ").append(extraction.getTotalPages()).append("\n");
        md.append("source_file_name: ").append(sourceFileName != null ? sourceFileName : "").append("\n");

        // 1차: 전체 텍스트에서 요구사항 번호 패턴 수집
        Matcher reqMatcher = REQ_NO_PATTERN.matcher(extraction.getFullText());
        while (reqMatcher.find()) {
            String prefix = reqMatcher.group(1).toUpperCase();
            String no = reqMatcher.group(2);
            String fullNo = prefix + "-" + String.format("%03d", Integer.parseInt(no));
            if (seenReqNos.add(fullNo)) {
                expectedBreakdown.merge(prefix, 1, Integer::sum);
            }
        }

        int expectedTotal = seenReqNos.size();
        md.append("expected_requirement_total: ").append(expectedTotal).append("\n");
        md.append("expected_requirement_breakdown:\n");
        expectedBreakdown.forEach((k, v) -> md.append("  ").append(k).append(": ").append(v).append("\n"));
        md.append("---\n\n");

        // 페이지별 블록 생성
        if (extraction.getPages() != null) {
            for (var page : extraction.getPages()) {
                int pageNo = page.getPageNo();
                md.append("## [PAGE ").append(pageNo).append("]\n\n");

                String pageText = page.getText();
                if (pageText == null || pageText.isBlank()) continue;

                // 문단 분리 (빈 줄 기준)
                String[] paragraphs = pageText.split("\n\\s*\n");
                int blockSeq = 0;

                for (String para : paragraphs) {
                    String trimmed = para.trim();
                    if (trimmed.isEmpty() || trimmed.length() < 5) continue;

                    blockSeq++;
                    String blockId = String.format("blk_p%d_%03d", pageNo, blockSeq);

                    // 요구사항 번호 감지
                    String clauseId = null;
                    Matcher m = REQ_NO_PATTERN.matcher(trimmed);
                    if (m.find()) {
                        clauseId = m.group(1).toUpperCase() + "-" + String.format("%03d", Integer.parseInt(m.group(2)));
                    }

                    // 의무 표현 감지
                    List<String> signals = new ArrayList<>();
                    for (String signal : OBLIGATION_SIGNALS) {
                        if (trimmed.contains(signal)) signals.add(signal);
                    }
                    boolean isCandidate = clauseId != null || !signals.isEmpty();

                    // 표 감지 (단순 휴리스틱)
                    String excerptType = trimmed.contains("|") && trimmed.lines().count() > 2 ? "TABLE" : "PARAGRAPH";

                    IntermediateFormat.Block block = IntermediateFormat.Block.builder()
                            .blockId(blockId)
                            .pageNo(pageNo)
                            .excerptType(excerptType)
                            .clauseId(clauseId)
                            .rawText(trimmed)
                            .requirementCandidate(isCandidate)
                            .requirementSignals(signals)
                            .build();
                    allBlocks.add(block);

                    // md 출력
                    md.append("### [BLOCK ").append(blockId).append("]");
                    if (clauseId != null) md.append(" [CLAUSE ").append(clauseId).append("]");
                    if (isCandidate) md.append(" [REQ_CANDIDATE]");
                    md.append("\n");
                    md.append(trimmed).append("\n\n");

                    // catalog 항목 등록 (요구사항 번호가 있는 블록)
                    if (clauseId != null && seenReqNos.contains(clauseId)) {
                        // 이미 catalog에 등록된 번호인지 확인
                        String finalClauseId = clauseId;
                        boolean alreadyInCatalog = catalog.stream()
                                .anyMatch(c -> c.getOriginalRequirementNo().equals(finalClauseId));
                        if (!alreadyInCatalog) {
                            String titleSnippet = trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
                            String group = clauseId.split("-")[0];
                            int seq = Integer.parseInt(clauseId.split("-")[1]);
                            catalog.add(IntermediateFormat.CatalogItem.builder()
                                    .originalRequirementNo(clauseId)
                                    .groupCode(group)
                                    .sequenceNo(seq)
                                    .originalTitle(titleSnippet)
                                    .pageNo(pageNo)
                                    .sourceBlockId(blockId)
                                    .status("DETECTED")
                                    .build());
                        }
                    }
                }
            }
        }

        // catalog에 없는 감지 번호 → UNCERTAIN으로 추가
        for (String reqNo : seenReqNos) {
            boolean inCatalog = catalog.stream()
                    .anyMatch(c -> c.getOriginalRequirementNo().equals(reqNo));
            if (!inCatalog) {
                String group = reqNo.split("-")[0];
                int seq = Integer.parseInt(reqNo.split("-")[1]);
                catalog.add(IntermediateFormat.CatalogItem.builder()
                        .originalRequirementNo(reqNo)
                        .groupCode(group)
                        .sequenceNo(seq)
                        .originalTitle("(텍스트에서 번호만 감지됨)")
                        .pageNo(0)
                        .sourceBlockId(null)
                        .status("UNCERTAIN")
                        .build());
            }
        }

        // 정렬
        catalog.sort(Comparator.comparing(IntermediateFormat.CatalogItem::getOriginalRequirementNo));

        log.info("[IntermediateFormat] 생성 완료: pages={} blocks={} catalog={} expected={}",
                extraction.getTotalPages(), allBlocks.size(), catalog.size(), expectedTotal);

        return IntermediateFormat.builder()
                .documentId(documentId)
                .projectId(projectId)
                .title(title)
                .pageCount(extraction.getTotalPages())
                .sourceFileName(sourceFileName)
                .normalizedMarkdown(md.toString())
                .blocks(allBlocks)
                .catalogItems(catalog)
                .expectedBreakdown(expectedBreakdown)
                .expectedTotal(expectedTotal)
                .build();
    }
}
