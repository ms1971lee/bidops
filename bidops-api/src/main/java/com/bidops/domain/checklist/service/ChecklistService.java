package com.bidops.domain.checklist.service;

import com.bidops.domain.checklist.dto.*;
import com.bidops.domain.checklist.enums.ChecklistItemStatus;
import com.bidops.domain.checklist.enums.RiskLevel;

import java.util.List;

public interface ChecklistService {

    // ── 체크리스트 묶음 ─────────────────────────────────────────────
    List<ChecklistDto> listChecklists(String projectId);

    ChecklistDto createChecklist(String projectId, ChecklistCreateRequest request);

    ChecklistDto getChecklist(String projectId, String checklistId);

    // ── 체크리스트 항목 ─────────────────────────────────────────────
    List<ChecklistItemDto> listItems(String checklistId,
                                      ChecklistItemStatus status,
                                      RiskLevel riskLevel,
                                      Boolean mandatory,
                                      String requirementId);

    ChecklistItemDto createItem(String checklistId, ChecklistItemCreateRequest request);

    ChecklistItemDto getItem(String checklistId, String itemId);

    ChecklistItemDto updateItem(String checklistId, String itemId, ChecklistItemUpdateRequest request);

    ChecklistItemDto changeItemStatus(String checklistId, String itemId, ChecklistItemStatusChangeRequest request);
}
