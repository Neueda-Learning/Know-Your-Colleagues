package com.example.knowyourcolleagues.alert.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.knowyourcolleagues.alert.dto.AlertDetailResponse;
import com.example.knowyourcolleagues.alert.dto.AlertResponse;
import com.example.knowyourcolleagues.alert.dto.CreateAlertCommand;
import com.example.knowyourcolleagues.alert.dto.UpdateAlertStatusRequest;
import com.example.knowyourcolleagues.alert.entity.Alert;
import com.example.knowyourcolleagues.alert.entity.AlertHistory;
import com.example.knowyourcolleagues.alert.enums.AlertStatus;
import com.example.knowyourcolleagues.alert.enums.Severity;
import com.example.knowyourcolleagues.alert.exception.AlertNotFoundException;
import com.example.knowyourcolleagues.alert.exception.InvalidAlertRequestException;
import com.example.knowyourcolleagues.alert.exception.InvalidAlertTransitionException;
import com.example.knowyourcolleagues.alert.mapper.AlertHistoryMapper;
import com.example.knowyourcolleagues.alert.mapper.AlertMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertServiceImplTest {

    @Mock
    private AlertMapper alertMapper;

    @Mock
    private AlertHistoryMapper alertHistoryMapper;

    private AlertServiceImpl alertService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        alertService = new AlertServiceImpl(alertMapper, alertHistoryMapper);
    }

    @Test
    void shouldCreateOpenAlertAndInitialHistory() {
        CreateAlertCommand command = validCreateCommand();
        when(alertMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(alertMapper.insert(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setId(101L);
            return 1;
        });
        when(alertHistoryMapper.insert(any(AlertHistory.class))).thenReturn(1);

        AlertResponse response = alertService.createAlert(command);

        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(response.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(response.getCreatedAt()).isNotNull();
        verify(alertMapper).insert(any(Alert.class));
        verify(alertHistoryMapper).insert(any(AlertHistory.class));
    }

    @Test
    void shouldReturnExistingAlertForDuplicateRuleAndTransaction() {
        Alert existing = openAlert();
        when(alertMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        AlertResponse response = alertService.createAlert(validCreateCommand());

        assertThat(response.getId()).isEqualTo(existing.getId());
        verify(alertMapper, never()).insert(any(Alert.class));
        verify(alertHistoryMapper, never()).insert(any(AlertHistory.class));
    }

    @Test
    void shouldAcknowledgeOpenAlertAndSetTimestamp() {
        Alert alert = openAlert();
        when(alertMapper.selectById(101L)).thenReturn(alert);
        when(alertMapper.updateById(alert)).thenReturn(1);
        when(alertHistoryMapper.insert(any(AlertHistory.class))).thenReturn(1);
        when(alertHistoryMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of());

        UpdateAlertStatusRequest request = new UpdateAlertStatusRequest();
        request.setTargetStatus(AlertStatus.ACKNOWLEDGED);

        AlertDetailResponse response =
                alertService.updateStatus(101L, request);

        assertThat(response.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(response.getAcknowledgedAt()).isNotNull();
        verify(alertMapper).updateById(alert);
        verify(alertHistoryMapper).insert(any(AlertHistory.class));
    }

    @ParameterizedTest
    @EnumSource(
            value = AlertStatus.class,
            names = {"OPEN", "INVESTIGATING", "CLOSED"}
    )
    void shouldRejectInvalidTransitionFromOpen(AlertStatus targetStatus) {
        Alert alert = openAlert();
        when(alertMapper.selectById(101L)).thenReturn(alert);

        UpdateAlertStatusRequest request = new UpdateAlertStatusRequest();
        request.setTargetStatus(targetStatus);

        assertThatThrownBy(() -> alertService.updateStatus(101L, request))
                .isInstanceOf(InvalidAlertTransitionException.class);

        verify(alertMapper, never()).updateById(any(Alert.class));
        verify(alertHistoryMapper, never()).insert(any(AlertHistory.class));
    }

    @Test
    void shouldRequireNotesWhenClosingAlert() {
        Alert alert = openAlert();
        alert.setStatus(AlertStatus.INVESTIGATING);
        when(alertMapper.selectById(101L)).thenReturn(alert);

        UpdateAlertStatusRequest request = new UpdateAlertStatusRequest();
        request.setTargetStatus(AlertStatus.CLOSED);

        assertThatThrownBy(() -> alertService.updateStatus(101L, request))
                .isInstanceOf(InvalidAlertRequestException.class)
                .hasMessageContaining("notes");

        verify(alertMapper, never()).updateById(any(Alert.class));
    }

    @Test
    void shouldThrowWhenAlertDoesNotExist() {
        when(alertMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> alertService.getAlert(999L))
                .isInstanceOf(AlertNotFoundException.class)
                .hasMessageContaining("999");
    }

    private CreateAlertCommand validCreateCommand() {
        CreateAlertCommand command = new CreateAlertCommand();
        command.setRuleId(1L);
        command.setTriggerTransactionId(5001L);
        command.setAccountId("ACC-001");
        command.setRuleName("Large Amount");
        command.setSeverity(Severity.HIGH);
        command.setTitle("Large transaction detected");
        command.setDescription("Amount exceeded configured threshold");
        return command;
    }

    private Alert openAlert() {
        LocalDateTime now = LocalDateTime.now();
        Alert alert = new Alert();
        alert.setId(101L);
        alert.setRuleId(1L);
        alert.setTriggerTransactionId(5001L);
        alert.setAccountId("ACC-001");
        alert.setRuleName("Large Amount");
        alert.setSeverity(Severity.HIGH);
        alert.setStatus(AlertStatus.OPEN);
        alert.setTitle("Large transaction detected");
        alert.setCreatedAt(now);
        alert.setUpdatedAt(now);
        alert.setVersion(0);
        return alert;
    }
}
