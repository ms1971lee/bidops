package com.bidops.domain.analysis.quality;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * generic 문장 차단 필터.
 * 아래 문구가 단독으로 사용되고 구체 수단이 없으면 실패 판정.
 */
@Component
public class GenericPhraseFilter {

    private static final List<String> BANNED_PHRASES = List.of(
            "체계적으로 관리한다",
            "체계적으로 관리",
            "신뢰를 줄 수 있다",
            "신뢰를 준다",
            "품질을 유지한다",
            "품질을 유지",
            "구체적으로 제시한다",
            "구체적으로 제시",
            "효율적으로 수행한다",
            "효율적으로 수행",
            "안정적 운영을 보장한다",
            "안정적 운영을 보장",
            "안정적으로 운영",
            "경쟁사 대비 차별화한다",
            "경쟁사 대비 차별화"
    );

    private static final List<String> EXECUTION_SIGNALS = List.of(
            "주체", "담당", "역할", "조직",      // 주체
            "절차", "단계", "프로세스", "워크플로우", // 절차
            "주기", "월간", "주간", "분기", "연간",  // 주기
            "도구", "시스템", "솔루션", "플랫폼",    // 도구
            "KPI", "지표", "목표치", "달성률",       // KPI
            "승인", "협의", "보고", "검토",         // 승인/협의 체계
            "산출물", "보고서", "계획서", "대장", "이력", // 산출물
            "예외", "비상", "장애", "복구",         // 예외 처리
            "이력관리", "변경이력", "추적"          // 이력 관리
    );

    /**
     * 텍스트가 generic 문장만으로 구성되어 있는지 검사.
     * @return true면 실패 (generic만 있고 실행 요소 없음)
     */
    public boolean isGenericOnly(String text) {
        if (text == null || text.isBlank()) return false; // 빈값은 별도 규칙으로 처리

        String normalized = text.trim();

        // 금지 문구 포함 여부
        boolean containsBanned = BANNED_PHRASES.stream()
                .anyMatch(p -> normalized.contains(p));

        if (!containsBanned) return false; // 금지 문구 없으면 통과

        // 실행 요소 포함 여부
        boolean hasExecutionSignal = EXECUTION_SIGNALS.stream()
                .anyMatch(s -> normalized.contains(s));

        // 금지 문구 있으나 실행 요소도 있으면 통과
        // 금지 문구 있고 실행 요소 없으면 실패
        return !hasExecutionSignal;
    }

    /**
     * implementation_approach에 How 요소(주체/절차/주기/산출물)가 충분한지 검사.
     * @return 포함된 How 요소 수 (0~4+)
     */
    public int countHowElements(String text) {
        if (text == null || text.isBlank()) return 0;

        int count = 0;
        // 주체
        if (containsAny(text, "주체", "담당", "역할", "조직", "팀", "인력", "관리자", "DBA", "PM", "지정", "배정")) count++;
        // 절차
        if (containsAny(text, "절차", "단계", "프로세스", "→", "워크플로우", "접수", "배정", "조치",
                "구축", "수행", "실시", "점검", "진단", "분석", "협의", "승인", "확인", "검증", "수립")) count++;
        // 주기
        if (containsAny(text, "주기", "월간", "주간", "분기", "연간", "일 1회", "주 1회", "월 1회",
                "정기", "수시", "월별", "분기별")) count++;
        // 산출물
        if (containsAny(text, "산출물", "보고서", "계획서", "대장", "리포트", "양식", "체크리스트",
                "문서", "기록", "목록", "확인서", "이력", "결과서")) count++;
        return count;
    }

    /**
     * differentiation_point에 구체 장치가 있는지 검사.
     * @return true면 구체 장치 포함
     */
    public boolean hasConcreteDevice(String text) {
        if (text == null || text.isBlank()) return false;
        return containsAny(text, "KPI", "자동화", "도구", "거버넌스", "이력관리", "영향도", "검증", "대시보드", "스크립트", "모니터링");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
