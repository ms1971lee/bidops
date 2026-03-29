package com.bidops.domain.analysis.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DocumentTextExtractorTest {

    @Nested
    @DisplayName("조항 번호 추출")
    class ClauseExtraction {

        @Test
        @DisplayName("숫자 조항 (1.1, 1.1.1)")
        void numericClauses() {
            String text = """
                    1. 사업 개요
                    1.1 추진 배경
                    본 사업은 ...
                    1.2 목적 및 범위
                    시스템 구축 ...
                    2. 기능 요구사항
                    2.1 사용자 관리
                    2.1.1 회원 가입
                    """;
            List<DocumentTextExtractor.ClauseInfo> clauses = DocumentTextExtractor.extractClauses(text);
            assertThat(clauses).hasSizeGreaterThanOrEqualTo(5);
            assertThat(clauses.get(0).clauseId()).isEqualTo("1.");
            assertThat(clauses.get(1).clauseId()).isEqualTo("1.1");
        }

        @Test
        @DisplayName("한글 조항 (제1조, 제2장)")
        void koreanClauses() {
            String text = """
                    제1조 목적
                    이 계약은 ...
                    제2조 계약 범위
                    제3장 일반 조건
                    """;
            List<DocumentTextExtractor.ClauseInfo> clauses = DocumentTextExtractor.extractClauses(text);
            assertThat(clauses).hasSizeGreaterThanOrEqualTo(3);
            assertThat(clauses.get(0).clauseId()).isEqualTo("제1조");
        }

        @Test
        @DisplayName("가/나/다 항목")
        void koreanLetterClauses() {
            String text = """
                    가. 기본 요구사항
                    나. 추가 요구사항
                    다. 선택 요구사항
                    """;
            List<DocumentTextExtractor.ClauseInfo> clauses = DocumentTextExtractor.extractClauses(text);
            assertThat(clauses).hasSize(3);
        }

        @Test
        @DisplayName("빈 텍스트 → 빈 목록")
        void emptyText() {
            assertThat(DocumentTextExtractor.extractClauses("")).isEmpty();
            assertThat(DocumentTextExtractor.extractClauses("일반 텍스트 내용")).isEmpty();
        }
    }

    @Nested
    @DisplayName("DocumentExtractionResult 구조")
    class ResultStructure {

        @Test
        @DisplayName("PageText 빌더 정상 동작")
        void pageTextBuilder() {
            var page = DocumentExtractionResult.PageText.builder()
                    .pageNo(1).text("테스트 내용").charCount(5)
                    .hasTable(true).isEmpty(false).build();
            assertThat(page.getPageNo()).isEqualTo(1);
            assertThat(page.isHasTable()).isTrue();
            assertThat(page.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("전체 결과 빌더")
        void resultBuilder() {
            var result = DocumentExtractionResult.builder()
                    .fullText("전체 텍스트")
                    .totalPages(3)
                    .pages(List.of())
                    .extractionMethod("PDFBOX")
                    .build();
            assertThat(result.getTotalPages()).isEqualTo(3);
            assertThat(result.getExtractionMethod()).isEqualTo("PDFBOX");
        }
    }
}
