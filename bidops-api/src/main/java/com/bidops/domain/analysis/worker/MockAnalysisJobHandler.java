package com.bidops.domain.analysis.worker;

import com.bidops.domain.analysis.entity.AnalysisJob;
import com.bidops.domain.analysis.enums.AnalysisJobType;
import com.bidops.domain.document.entity.Document;
import com.bidops.domain.document.enums.DocumentParseStatus;
import com.bidops.domain.document.repository.DocumentRepository;
import com.bidops.domain.project.entity.Project;
import com.bidops.domain.project.enums.ProjectStatus;
import com.bidops.domain.project.repository.ProjectRepository;
import com.bidops.common.storage.StorageService;
import com.bidops.domain.document.entity.SourceExcerpt;
import com.bidops.domain.document.repository.SourceExcerptRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.core.io.Resource;
import com.bidops.domain.requirement.entity.Requirement;
import com.bidops.domain.requirement.entity.RequirementInsight;
import com.bidops.domain.requirement.entity.RequirementSource;
import com.bidops.domain.requirement.enums.FactLevel;
import com.bidops.domain.requirement.enums.RequirementCategory;
import com.bidops.domain.requirement.repository.RequirementInsightRepository;
import com.bidops.domain.requirement.repository.RequirementRepository;
import com.bidops.domain.requirement.repository.RequirementSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 로컬 개발용 Mock 핸들러.
 * local 프로파일에서만 활성화.
 * TODO: stub - AI 워커 연동 시 AiAnalysisJobHandler로 대체
 */
@Slf4j
@Component
@Profile("local")
@Order(100)
@RequiredArgsConstructor
public class MockAnalysisJobHandler implements AnalysisJobHandler {

    private final DocumentRepository documentRepository;
    private final ProjectRepository projectRepository;
    private final RequirementRepository requirementRepository;
    private final RequirementInsightRepository insightRepository;
    private final RequirementSourceRepository sourceRepository;
    private final SourceExcerptRepository excerptRepository;
    private final StorageService storageService;

    @Override
    public boolean supports(AnalysisJob job) {
        return true;
    }

    @Override
    @Transactional
    public int execute(AnalysisJob job) {
        return execute(job, (p) -> {});
    }

    @Override
    @Transactional
    public int execute(AnalysisJob job, ProgressCallback callback) {
        log.info("[MockHandler] Job 실행 (mock): jobId={} type={}", job.getId(), job.getJobType());

        callback.report(5);

        // 문서 상태 → PARSING
        documentRepository.findById(job.getDocumentId()).ifPresent(doc -> {
            doc.updateParseStatus(DocumentParseStatus.PARSING);
            documentRepository.save(doc);
        });

        callback.report(10);

        // 프로젝트 상태 → ANALYZING
        projectRepository.findById(job.getProjectId()).ifPresent(p -> {
            p.changeStatus(ProjectStatus.ANALYZING);
            projectRepository.save(p);
        });

        callback.report(20);

        // OCR 시뮬레이션
        sleep(1000);
        callback.report(35);

        // 텍스트 추출 시뮬레이션
        sleep(1000);
        callback.report(50);

        int resultCount = 0;

        if (job.getJobType() == AnalysisJobType.RFP_PARSE) {
            resultCount = generateMockRequirements(job, callback);
        }

        callback.report(90);

        // 문서 상태 → PARSED + 실제 페이지 수
        documentRepository.findById(job.getDocumentId()).ifPresent(doc -> {
            doc.updateParseStatus(DocumentParseStatus.PARSED);
            doc.updatePageCount(readPdfPageCount(doc.getStoragePath()));
            documentRepository.save(doc);
        });

        // 프로젝트 상태 → REVIEWING
        projectRepository.findById(job.getProjectId()).ifPresent(p -> {
            p.changeStatus(ProjectStatus.REVIEWING);
            projectRepository.save(p);
        });

        callback.report(95);
        sleep(500);

        log.info("[MockHandler] Job 완료 (mock): jobId={} resultCount={}", job.getId(), resultCount);
        return resultCount;
    }

    private int readPdfPageCount(String storagePath) {
        try {
            Resource resource = storageService.load(storagePath);
            try (PDDocument pdf = Loader.loadPDF(resource.getInputStream().readAllBytes())) {
                return pdf.getNumberOfPages();
            }
        } catch (Exception e) {
            log.warn("[MockHandler] PDF 페이지 수 읽기 실패: {}", e.getMessage());
            return 0;
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Job 실행 중 인터럽트", e);
        }
    }

    /** 재분석 시 기존 문서 기준 requirement 관련 데이터 정리 */
    private void cleanExistingData(String documentId) {
        List<Requirement> existing = requirementRepository.findByDocumentId(documentId);
        for (Requirement req : existing) {
            sourceRepository.deleteByRequirementId(req.getId());
            insightRepository.deleteByRequirementId(req.getId());
        }
        excerptRepository.deleteByDocumentId(documentId);
        requirementRepository.deleteAll(existing);
        log.info("[MockHandler] 기존 데이터 정리 완료: documentId={} requirements={}", documentId, existing.size());
    }

    /** stub: 샘플 요구사항 + Insight + Source 생성 */
    private int generateMockRequirements(AnalysisJob job, ProgressCallback callback) {
        cleanExistingData(job.getDocumentId());
        callback.report(55);
        record MockReq(String code, String title, String text, RequirementCategory cat,
                       boolean mandatory, FactLevel fact, int pageNo, String clause,
                       String factSummary, String intentSummary, String proposalPoint,
                       String implApproach, String deliverables, String risk) {}

        List<MockReq> samples = List.of(
            new MockReq("SFR-001", "사용자 인증 기능",
                "시스템은 사용자 인증을 위해 ID/PW 기반 로그인 기능을 제공해야 한다.",
                RequirementCategory.FUNCTIONAL, true, FactLevel.FACT, 3, "3.1.1",
                "ID/PW 기반 로그인 기능 필수 요구", "사용자 접근 통제를 위한 기본 인증 체계 구축 요구",
                "다중 인증(MFA) 지원으로 보안 차별화", "Spring Security + JWT 기반 구현",
                "[\"로그인 화면 설계서\",\"인증 모듈 소스코드\"]", "[\"세션 탈취 위험\"]"),
            new MockReq("SFR-002", "권한 관리",
                "시스템은 역할 기반 접근 제어(RBAC)를 지원해야 한다.",
                RequirementCategory.FUNCTIONAL, true, FactLevel.FACT, 3, "3.1.2",
                "RBAC 기반 권한 관리 명시", "조직 구조에 맞는 세분화된 접근 제어 요구",
                "동적 역할 정의 + 메뉴별 권한 매핑 제안", "역할-권한 매핑 테이블 + AOP 기반 체크",
                "[\"권한 관리 설계서\"]", "[\"역할 과다 생성 시 관리 복잡도 증가\"]"),
            new MockReq("NFR-001", "응답 시간",
                "모든 화면의 응답 시간은 3초 이내여야 한다.",
                RequirementCategory.PERFORMANCE, true, FactLevel.FACT, 5, "4.1",
                "전체 화면 응답 시간 3초 이내 기준 명시", "사용자 체감 성능 보장 요구",
                "CDN + 캐싱 전략으로 1초 이내 달성 목표 제안", "Redis 캐시 + 쿼리 최적화",
                "[\"성능 테스트 결과서\"]", "[\"대용량 데이터 조회 시 3초 초과 가능\"]"),
            new MockReq("NFR-002", "동시 접속",
                "동시 사용자 100명 이상을 지원해야 한다.",
                RequirementCategory.PERFORMANCE, false, FactLevel.INFERENCE, 5, "4.2",
                "동시 접속 100명 이상 지원 요구 (추론)", "시스템 확장성 확보 요구",
                "Auto Scaling 기반 탄력적 인프라 제안", "k8s HPA + 로드밸런서",
                "[\"인프라 구성도\",\"부하 테스트 보고서\"]", "[]"),
            new MockReq("SEC-001", "데이터 암호화",
                "개인정보는 AES-256 이상으로 암호화하여 저장해야 한다.",
                RequirementCategory.SECURITY, true, FactLevel.FACT, 7, "5.1",
                "AES-256 이상 암호화 저장 의무", "개인정보보호법 준수를 위한 기술적 조치 요구",
                "AES-256-GCM + DB 컬럼 암호화 적용", "JPA AttributeConverter 기반 자동 암호화",
                "[\"보안 설계서\",\"암호화 적용 증적\"]", "[\"암호화 키 관리 체계 필요\"]"),
            new MockReq("INF-001", "클라우드 환경",
                "시스템은 클라우드 환경(AWS 또는 동급)에 배포 가능해야 한다.",
                RequirementCategory.INFRASTRUCTURE, false, FactLevel.INFERENCE, 8, "6.1",
                "클라우드 배포 가능성 요구 (AWS 명시)", "온프레미스 종속 배제, 클라우드 네이티브 지향",
                "AWS 기반 3-tier 아키텍처 제안", "ECS Fargate + RDS + S3 구성",
                "[\"인프라 구성도\",\"배포 가이드\"]", "[\"클라우드 비용 최적화 필요\"]"),
            new MockReq("DEL-001", "산출물 목록",
                "요구사항 정의서, 설계서, 테스트 결과서를 제출해야 한다.",
                RequirementCategory.DELIVERABLE, true, FactLevel.FACT, 10, "8.1",
                "3종 산출물 필수 제출 명시", "프로젝트 품질 검증을 위한 문서 산출물 요구",
                "표준 템플릿 기반 산출물 자동 생성 제안", "문서 자동화 도구 연계",
                "[\"요구사항 정의서\",\"설계서\",\"테스트 결과서\"]", "[]"),
            new MockReq("SCH-001", "프로젝트 일정",
                "개발 기간은 계약일로부터 6개월 이내로 한다.",
                RequirementCategory.SCHEDULE, true, FactLevel.REVIEW_NEEDED, 11, "9.1",
                "6개월 개발 기간 제한", "프로젝트 일정 준수 의지 확인 필요",
                "Agile 2주 스프린트 기반 점진적 납품 제안", "2주 스프린트 × 12회 = 24주",
                "[\"프로젝트 일정표\",\"WBS\"]", "[\"범위 변경 시 일정 초과 위험\"]")
        );

        int count = 0;
        int total = samples.size();
        for (int i = 0; i < total; i++) {
            MockReq m = samples.get(i);
            // 55~85% 범위에서 항목별 진행률
            callback.report(55 + (int)((i + 1) * 30.0 / total));
            sleep(300); // 항목별 처리 시뮬레이션

            if (requirementRepository.existsByDocumentIdAndOriginalText(job.getDocumentId(), m.text())) {
                continue;
            }

            // 1) Requirement 저장
            Requirement req = requirementRepository.save(Requirement.builder()
                    .projectId(job.getProjectId())
                    .documentId(job.getDocumentId())
                    .requirementCode(m.code())
                    .title(m.title())
                    .originalText(m.text())
                    .category(m.cat())
                    .mandatoryFlag(m.mandatory())
                    .evidenceRequiredFlag(m.mandatory())
                    .confidenceScore(0.85f)
                    .factLevel(m.fact())
                    .build());

            // 2) RequirementInsight 저장
            insightRepository.save(RequirementInsight.builder()
                    .requirementId(req.getId())
                    .factSummary(m.factSummary())
                    .interpretationSummary(m.code() + " 해석: 발주처는 " + m.title() + " 구현을 요구하고 있다.")
                    .intentSummary(m.intentSummary())
                    .proposalPoint(m.proposalPoint())
                    .implementationApproach(m.implApproach())
                    .expectedDeliverablesJson(m.deliverables())
                    .differentiationPoint(m.proposalPoint())
                    .riskNoteJson(m.risk())
                    .queryNeeded(m.fact() == FactLevel.REVIEW_NEEDED)
                    .factLevel(m.fact())
                    .generatedByJobId(job.getId())
                    .build());

            // 3) SourceExcerpt 저장
            SourceExcerpt excerpt = excerptRepository.save(SourceExcerpt.builder()
                    .documentId(job.getDocumentId())
                    .pageNo(m.pageNo())
                    .excerptType(SourceExcerpt.ExcerptType.PARAGRAPH)
                    .anchorLabel(m.clause())
                    .rawText(m.text())
                    .normalizedText(m.text())
                    .build());

            // 4) RequirementSource 연결
            sourceRepository.save(RequirementSource.builder()
                    .requirementId(req.getId())
                    .sourceExcerptId(excerpt.getId())
                    .linkType(RequirementSource.LinkType.PRIMARY)
                    .build());

            count++;
        }
        return count;
    }
}
