-- ============================================================
-- V001: RequirementCategory enum 변경 마이그레이션
--   STAFFING   → PERSONNEL
--   EXPERIENCE → TRACK_RECORD
--
-- 적용 대상: requirements.category, requirements.fact_level (변경 없음)
-- 작성일: 2026-03-19
-- ============================================================

-- ■ 1단계: 적용 전 확인 (영향 행 수 파악)
SELECT category, COUNT(*) AS cnt
FROM requirements
WHERE category IN ('STAFFING', 'EXPERIENCE')
GROUP BY category;

-- ■ 2단계: 마이그레이션 실행
UPDATE requirements SET category = 'PERSONNEL'    WHERE category = 'STAFFING';
UPDATE requirements SET category = 'TRACK_RECORD' WHERE category = 'EXPERIENCE';

-- ■ 3단계: 적용 후 검증
-- 3-1. 기존 값이 남아있으면 안 됨 (결과: 0건이어야 정상)
SELECT category, COUNT(*) AS cnt
FROM requirements
WHERE category IN ('STAFFING', 'EXPERIENCE')
GROUP BY category;

-- 3-2. 새 값으로 변환된 행 확인
SELECT category, COUNT(*) AS cnt
FROM requirements
WHERE category IN ('PERSONNEL', 'TRACK_RECORD')
GROUP BY category;

-- 3-3. 전체 카테고리 분포 확인
SELECT category, COUNT(*) AS cnt
FROM requirements
GROUP BY category
ORDER BY cnt DESC;
