package com.bidops.domain.project.service;

import com.bidops.auth.User;
import com.bidops.auth.UserRepository;
import com.bidops.common.response.ListData;
import com.bidops.domain.project.dto.ProjectActivityLogDto;
import com.bidops.domain.project.entity.ProjectActivityLog;
import com.bidops.domain.project.enums.ActivityType;
import com.bidops.domain.project.repository.ProjectActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectActivityService {

    private final ProjectActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    /**
     * 활동 기록.
     */
    @Transactional
    public void record(String projectId, ActivityType type, String summary,
                       String actorUserId, String targetId, String targetType, String detail) {
        activityLogRepository.save(ProjectActivityLog.builder()
                .projectId(projectId)
                .activityType(type)
                .summary(summary)
                .actorUserId(actorUserId)
                .targetId(targetId)
                .targetType(targetType)
                .detail(detail)
                .build());
    }

    /**
     * 활동 이력 조회.
     */
    @Transactional(readOnly = true)
    public ListData<ProjectActivityLogDto> listActivities(String projectId,
                                                           ActivityType activityType,
                                                           String targetType,
                                                           String actorUserId,
                                                           LocalDateTime dateFrom,
                                                           LocalDateTime dateTo,
                                                           int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size);
        Page<ProjectActivityLog> result = activityLogRepository.search(
                projectId, activityType, targetType, actorUserId, dateFrom, dateTo, pageable);

        // actor name 일괄 조회
        List<String> userIds = result.getContent().stream()
                .map(ProjectActivityLog::getActorUserId)
                .distinct().toList();
        Map<String, String> nameMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));

        List<ProjectActivityLogDto> items = result.getContent().stream()
                .map(a -> ProjectActivityLogDto.from(a, nameMap.getOrDefault(a.getActorUserId(), "알 수 없음")))
                .toList();

        return new ListData<>(items, result.getTotalElements());
    }
}
