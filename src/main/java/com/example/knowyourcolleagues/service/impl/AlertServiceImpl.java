package com.example.knowyourcolleagues.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.knowyourcolleagues.service.AlertService;
import com.example.knowyourcolleagues.dto.AlertDetailResponse;
import com.example.knowyourcolleagues.dto.AlertHistoryResponse;
import com.example.knowyourcolleagues.dto.AlertPageResponse;
import com.example.knowyourcolleagues.dto.AlertResponse;
import com.example.knowyourcolleagues.dto.CreateAlertCommand;
import com.example.knowyourcolleagues.dto.UpdateAlertStatusRequest;
import com.example.knowyourcolleagues.entity.Alert;
import com.example.knowyourcolleagues.entity.AlertHistory;
import com.example.knowyourcolleagues.entity.AlertTransaction;
import com.example.knowyourcolleagues.enums.AlertStatus;
import com.example.knowyourcolleagues.enums.Severity;
import com.example.knowyourcolleagues.bizexception.alert.AlertNotFoundException;
import com.example.knowyourcolleagues.bizexception.alert.ConcurrentAlertUpdateException;
import com.example.knowyourcolleagues.bizexception.alert.InvalidAlertRequestException;
import com.example.knowyourcolleagues.bizexception.alert.InvalidAlertTransitionException;
import com.example.knowyourcolleagues.mapper.AlertHistoryMapper;
import com.example.knowyourcolleagues.mapper.AlertMapper;
import com.example.knowyourcolleagues.mapper.AlertTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private static final long MAX_PAGE_SIZE = 100;

    private static final Map<AlertStatus, Set<AlertStatus>> ALLOWED_TRANSITIONS =
            createAllowedTransitions();

    private final AlertMapper alertMapper;
    private final AlertHistoryMapper alertHistoryMapper;
    private final AlertTransactionMapper alertTransactionMapper;
    private final Clock clock = Clock.systemUTC();

    @Override
    @Transactional
    public AlertResponse createAlert(CreateAlertCommand command) {
        validateCreateCommand(command);
        List<Long> relatedTransactionIds =
                normalizeRelatedTransactionIds(command);

        Alert existing = findByRuleAndTransaction(
                command.getRuleId(),
                command.getTriggerTransactionId()
        );
        if (existing != null) {
            return toResponse(existing);
        }

        Instant now = clock.instant();
        Alert alert = new Alert();
        alert.setRuleId(command.getRuleId());
        alert.setTriggerTransactionId(command.getTriggerTransactionId());
        alert.setAccountId(command.getAccountId().trim());
        alert.setRuleName(command.getRuleName().trim());
        alert.setSeverity(command.getSeverity());
        alert.setStatus(AlertStatus.OPEN);
        alert.setTitle(command.getTitle().trim());
        alert.setDescription(trimToNull(command.getDescription()));
        alert.setCreatedAt(now);
        alert.setUpdatedAt(now);
        alert.setVersion(0);

        try {
            alertMapper.insert(alert);
        } catch (DuplicateKeyException exception) {
            Alert duplicate = findByRuleAndTransaction(
                    command.getRuleId(),
                    command.getTriggerTransactionId()
            );
            if (duplicate != null) {
                return toResponse(duplicate);
            }
            throw exception;
        }

        saveHistory(alert.getId(), null, AlertStatus.OPEN,
                "Alert created by rule evaluation", now);
        saveRelatedTransactions(alert.getId(), relatedTransactionIds);
        return toResponse(alert);
    }

    @Override
    @Transactional(readOnly = true)
    public AlertDetailResponse getAlert(Long alertId) {
        Alert alert = requireAlert(alertId);
        return toDetailResponse(
                alert,
                loadHistory(alertId),
                loadRelatedTransactionIds(alertId)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AlertPageResponse getAlerts(
            AlertStatus status,
            Severity severity,
            String accountId,
            long page,
            long size
    ) {
        validatePage(page, size);

        LambdaQueryWrapper<Alert> query = new LambdaQueryWrapper<Alert>()
                .eq(status != null, Alert::getStatus, status)
                .eq(severity != null, Alert::getSeverity, severity)
                .eq(hasText(accountId), Alert::getAccountId,
                        hasText(accountId) ? accountId.trim() : null)
                .orderByDesc(Alert::getCreatedAt);

        List<Alert> matchingAlerts = alertMapper.selectList(query);
        long fromIndex = Math.min(page * size, matchingAlerts.size());
        long toIndex = Math.min(fromIndex + size, matchingAlerts.size());
        List<Alert> pageContent = matchingAlerts.subList(
                Math.toIntExact(fromIndex),
                Math.toIntExact(toIndex)
        );
        long totalElements = matchingAlerts.size();
        long totalPages = totalElements == 0
                ? 0
                : (totalElements + size - 1) / size;

        AlertPageResponse response = new AlertPageResponse();
        response.setContent(pageContent.stream()
                .map(this::toResponse)
                .toList());
        response.setPage(page);
        response.setSize(size);
        response.setTotalElements(totalElements);
        response.setTotalPages(totalPages);
        return response;
    }

    @Override
    @Transactional
    public AlertDetailResponse updateStatus(
            Long alertId,
            UpdateAlertStatusRequest request
    ) {
        if (request == null || request.getTargetStatus() == null) {
            throw new InvalidAlertRequestException("targetStatus is required");
        }

        Alert alert = requireAlert(alertId);
        AlertStatus fromStatus = alert.getStatus();
        AlertStatus targetStatus = request.getTargetStatus();
        validateTransition(fromStatus, targetStatus);
        validateResolutionNotes(targetStatus, request.getNotes());

        Instant now = clock.instant();
        alert.setStatus(targetStatus);
        alert.setUpdatedAt(now);
        applyStatusTimestamp(alert, targetStatus, now);

        if (targetStatus == AlertStatus.CLOSED
                || targetStatus == AlertStatus.DISMISSED) {
            alert.setResolutionNotes(request.getNotes().trim());
        }

        int updatedRows = alertMapper.updateById(alert);
        if (updatedRows != 1) {
            throw new ConcurrentAlertUpdateException(
                    "Alert was updated by another request: " + alertId
            );
        }

        saveHistory(
                alertId,
                fromStatus,
                targetStatus,
                trimToNull(request.getNotes()),
                now
        );
        return toDetailResponse(
                alert,
                loadHistory(alertId),
                loadRelatedTransactionIds(alertId)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertHistoryResponse> getHistory(Long alertId) {
        requireAlert(alertId);
        return loadHistory(alertId);
    }

    private Alert requireAlert(Long alertId) {
        if (alertId == null || alertId <= 0) {
            throw new InvalidAlertRequestException("alertId must be positive");
        }

        Alert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new AlertNotFoundException("Alert not found: " + alertId);
        }
        return alert;
    }

    private Alert findByRuleAndTransaction(Long ruleId, Long transactionId) {
        return alertMapper.selectOne(
                new LambdaQueryWrapper<Alert>()
                        .eq(Alert::getRuleId, ruleId)
                        .eq(Alert::getTriggerTransactionId, transactionId)
                        .last("LIMIT 1")
        );
    }

    private List<AlertHistoryResponse> loadHistory(Long alertId) {
        return alertHistoryMapper.selectList(
                        new LambdaQueryWrapper<AlertHistory>()
                                .eq(AlertHistory::getAlertId, alertId)
                                .orderByAsc(AlertHistory::getChangedAt)
                                .orderByAsc(AlertHistory::getId)
                )
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private List<Long> loadRelatedTransactionIds(Long alertId) {
        return alertTransactionMapper.selectList(
                        new LambdaQueryWrapper<AlertTransaction>()
                                .eq(AlertTransaction::getAlertId, alertId)
                                .orderByAsc(AlertTransaction::getId)
                )
                .stream()
                .map(AlertTransaction::getTransactionId)
                .toList();
    }

    private void saveHistory(
            Long alertId,
            AlertStatus fromStatus,
            AlertStatus toStatus,
            String notes,
            Instant changedAt
    ) {
        AlertHistory history = new AlertHistory();
        history.setAlertId(alertId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setNotes(notes);
        history.setChangedAt(changedAt);
        alertHistoryMapper.insert(history);
    }

    private void saveRelatedTransactions(
            Long alertId,
            List<Long> transactionIds
    ) {
        for (Long transactionId : transactionIds) {
            AlertTransaction relation = new AlertTransaction();
            relation.setAlertId(alertId);
            relation.setTransactionId(transactionId);
            alertTransactionMapper.insert(relation);
        }
    }

    private List<Long> normalizeRelatedTransactionIds(
            CreateAlertCommand command
    ) {
        LinkedHashSet<Long> transactionIds = new LinkedHashSet<>();
        transactionIds.add(command.getTriggerTransactionId());

        if (command.getRelatedTransactionIds() != null) {
            for (Long transactionId : command.getRelatedTransactionIds()) {
                if (transactionId == null || transactionId <= 0) {
                    throw new InvalidAlertRequestException(
                            "relatedTransactionIds must contain only positive IDs"
                    );
                }
                transactionIds.add(transactionId);
            }
        }

        return new ArrayList<>(transactionIds);
    }

    private void validateCreateCommand(CreateAlertCommand command) {
        if (command == null) {
            throw new InvalidAlertRequestException("create alert command is required");
        }
        if (command.getRuleId() == null || command.getRuleId() <= 0) {
            throw new InvalidAlertRequestException("ruleId must be positive");
        }
        if (command.getTriggerTransactionId() == null
                || command.getTriggerTransactionId() <= 0) {
            throw new InvalidAlertRequestException(
                    "triggerTransactionId must be positive"
            );
        }
        if (!hasText(command.getAccountId())) {
            throw new InvalidAlertRequestException("accountId is required");
        }
        if (!hasText(command.getRuleName())) {
            throw new InvalidAlertRequestException("ruleName is required");
        }
        if (command.getSeverity() == null) {
            throw new InvalidAlertRequestException("severity is required");
        }
        if (!hasText(command.getTitle())) {
            throw new InvalidAlertRequestException("title is required");
        }
    }

    private void validatePage(long page, long size) {
        if (page < 0) {
            throw new InvalidAlertRequestException("page must not be negative");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new InvalidAlertRequestException(
                    "size must be between 1 and " + MAX_PAGE_SIZE
            );
        }
    }

    private void validateTransition(AlertStatus from, AlertStatus to) {
        if (from == null) {
            throw new InvalidAlertTransitionException(
                    "Alert has no current status"
            );
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new InvalidAlertTransitionException(
                    "Alert cannot transition from " + from + " to " + to
            );
        }
    }

    private void validateResolutionNotes(AlertStatus targetStatus, String notes) {
        if ((targetStatus == AlertStatus.CLOSED
                || targetStatus == AlertStatus.DISMISSED)
                && !hasText(notes)) {
            throw new InvalidAlertRequestException(
                    "notes are required when closing or dismissing an alert"
            );
        }
    }

    private void applyStatusTimestamp(
            Alert alert,
            AlertStatus targetStatus,
            Instant now
    ) {
        switch (targetStatus) {
            case ACKNOWLEDGED -> alert.setAcknowledgedAt(now);
            case INVESTIGATING -> alert.setInvestigatingAt(now);
            case CLOSED -> alert.setClosedAt(now);
            case DISMISSED -> alert.setDismissedAt(now);
            case OPEN -> throw new InvalidAlertTransitionException(
                    "An existing alert cannot transition to OPEN"
            );
        }
    }

    private AlertResponse toResponse(Alert alert) {
        AlertResponse response = new AlertResponse();
        response.setId(alert.getId());
        response.setRuleId(alert.getRuleId());
        response.setTriggerTransactionId(alert.getTriggerTransactionId());
        response.setAccountId(alert.getAccountId());
        response.setRuleName(alert.getRuleName());
        response.setSeverity(alert.getSeverity());
        response.setStatus(alert.getStatus());
        response.setTitle(alert.getTitle());
        response.setCreatedAt(alert.getCreatedAt());
        response.setUpdatedAt(alert.getUpdatedAt());
        return response;
    }

    private AlertDetailResponse toDetailResponse(
            Alert alert,
            List<AlertHistoryResponse> history,
            List<Long> relatedTransactionIds
    ) {
        AlertDetailResponse response = new AlertDetailResponse();
        response.setId(alert.getId());
        response.setRuleId(alert.getRuleId());
        response.setTriggerTransactionId(alert.getTriggerTransactionId());
        response.setAccountId(alert.getAccountId());
        response.setRuleName(alert.getRuleName());
        response.setSeverity(alert.getSeverity());
        response.setStatus(alert.getStatus());
        response.setTitle(alert.getTitle());
        response.setDescription(alert.getDescription());
        response.setResolutionNotes(alert.getResolutionNotes());
        response.setCreatedAt(alert.getCreatedAt());
        response.setAcknowledgedAt(alert.getAcknowledgedAt());
        response.setInvestigatingAt(alert.getInvestigatingAt());
        response.setClosedAt(alert.getClosedAt());
        response.setDismissedAt(alert.getDismissedAt());
        response.setUpdatedAt(alert.getUpdatedAt());
        response.setVersion(alert.getVersion());
        response.setRelatedTransactionIds(relatedTransactionIds);
        response.setHistory(history);
        return response;
    }

    private AlertHistoryResponse toHistoryResponse(AlertHistory history) {
        AlertHistoryResponse response = new AlertHistoryResponse();
        response.setId(history.getId());
        response.setFromStatus(history.getFromStatus());
        response.setToStatus(history.getToStatus());
        response.setNotes(history.getNotes());
        response.setChangedAt(history.getChangedAt());
        return response;
    }

    private static Map<AlertStatus, Set<AlertStatus>> createAllowedTransitions() {
        Map<AlertStatus, Set<AlertStatus>> transitions =
                new EnumMap<>(AlertStatus.class);
        transitions.put(
                AlertStatus.OPEN,
                Set.of(AlertStatus.ACKNOWLEDGED, AlertStatus.DISMISSED)
        );
        transitions.put(
                AlertStatus.ACKNOWLEDGED,
                Set.of(AlertStatus.INVESTIGATING, AlertStatus.DISMISSED)
        );
        transitions.put(
                AlertStatus.INVESTIGATING,
                Set.of(AlertStatus.CLOSED, AlertStatus.DISMISSED)
        );
        transitions.put(AlertStatus.CLOSED, Set.of());
        transitions.put(AlertStatus.DISMISSED, Set.of());
        return Map.copyOf(transitions);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
