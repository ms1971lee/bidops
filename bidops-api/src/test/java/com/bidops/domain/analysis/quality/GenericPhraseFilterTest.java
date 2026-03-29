package com.bidops.domain.analysis.quality;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenericPhraseFilterTest {

    private final GenericPhraseFilter filter = new GenericPhraseFilter();

    // ── isGenericOnly ──────────────────────────────────────────

    @Test
    void genericOnly_shouldFail() {
        assertTrue(filter.isGenericOnly("체계적으로 관리한다"));
        assertTrue(filter.isGenericOnly("데이터 품질을 유지한다"));
        assertTrue(filter.isGenericOnly("안정적 운영을 보장한다"));
    }

    @Test
    void genericWithExecution_shouldPass() {
        assertFalse(filter.isGenericOnly("체계적으로 관리한다. DBA가 월 1회 점검하고 리포트를 산출물로 제출"));
        assertFalse(filter.isGenericOnly("품질을 유지한다. 자동 검증 스크립트로 KPI 95% 달성"));
    }

    @Test
    void noGenericPhrase_shouldPass() {
        assertFalse(filter.isGenericOnly("DBA가 월간 표준 준수율을 점검하고 보고서를 제출한다"));
    }

    @Test
    void blank_shouldPass() {
        assertFalse(filter.isGenericOnly(null));
        assertFalse(filter.isGenericOnly(""));
    }

    // ── countHowElements ──────────────────────────────────────

    @Test
    void howElements_allPresent() {
        String text = "DBA(주체)가 월 1회(주기) DB 표준 점검을 실시하고(절차), 점검 결과를 보고서(산출물)로 작성";
        assertEquals(4, filter.countHowElements(text));
    }

    @Test
    void howElements_partial() {
        String text = "담당자가 점검을 실시한다";
        assertEquals(2, filter.countHowElements(text)); // 주체(담당) + 절차(점검, 실시)
    }

    @Test
    void howElements_none() {
        assertEquals(0, filter.countHowElements("데이터 표준을 관리한다"));
    }

    // ── hasConcreteDevice ─────────────────────────────────────

    @Test
    void concreteDevice_present() {
        assertTrue(filter.hasConcreteDevice("KPI 95% 달성을 목표로 자동화 도구 적용"));
        assertTrue(filter.hasConcreteDevice("변경이력 대시보드를 통한 거버넌스 체계 수립"));
    }

    @Test
    void concreteDevice_absent() {
        assertFalse(filter.hasConcreteDevice("전문 인력을 투입하여 차별화한다"));
        assertFalse(filter.hasConcreteDevice("최적의 방안을 제시한다"));
    }
}
