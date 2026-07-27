package com.example.knowyourcolleagues.alert.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.knowyourcolleagues.dto.AlertDetailResponse;
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
import com.example.knowyourcolleagues.bizexception.alert.InvalidAlertRequestException;
import com.example.knowyourcolleagues.bizexception.alert.InvalidAlertTransitionException;
import com.example.knowyourcolleagues.mapper.AlertHistoryMapper;
import com.example.knowyourcolleagues.mapper.AlertMapper;
import com.example.knowyourcolleagues.mapper.AlertTransactionMapper;
import com.example.knowyourcolleagues.service.impl.AlertServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertServiceImplTest {

    @Mock
    private AlertMapper alertMapper;

    @Mock
    private AlertHistoryMapper alertHistoryMapper;

    @Mock
    private AlertTransactionMapper alertTransactionMapper;

    private AlertServiceImpl alertService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        alertService = new AlertServiceImpl(
                alertMapper,
                alertHistoryMapper,
                alertTransactionMapper
        );
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
        when(alertTransactionMapper.insert(any(AlertTransaction.class)))
                .thenReturn(1);

        AlertResponse response = alertService.createAlert(command);

        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(response.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(response.getCreatedAt()).isNotNull();
        verify(alertMapper).insert(any(Alert.class));
        verify(alertHistoryMapper).insert(any(AlertHistory.class));
        verify(alertTransactionMapper, times(2))
                .insert(any(AlertTransaction.class));
    }

    @Test
    void shouldReturnExistingAlertForDuplicateRuleAndTransaction() {
        Alert existing = openAlert();
        when(alertMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        AlertResponse response = alertService.createAlert(validCreateCommand());

        assertThat(response.getId()).isEqualTo(existing.getId());
        verify(alertMapper, never()).insert(any(Alert.class));
        verify(alertHistoryMapper, never()).insert(any(AlertHistory.class));
        verify(alertTransactionMapper, never())
                .insert(any(AlertTransaction.class));
    }

    @Test
    void shouldAcknowledgeOpenAlertAndSetTimestamp() {
        Alert alert = openAlert();
        when(alertMapper.selectById(101L)).thenReturn(alert);
        when(alertMapper.updateById(alert)).thenReturn(1);
        when(alertHistoryMapper.insert(any(AlertHistory.class))).thenReturn(1);
        when(alertHistoryMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of());
        when(alertTransactionMapper.selectList(any(Wrapper.class)))
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

    @Test
    @SuppressWarnings("unchecked")
    void shouldQueryAlertsWithDatabasePagination() {
        Alert alert = openAlert();
        when(alertMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    Page<Alert> requestedPage = invocation.getArgument(0);
                    requestedPage.setRecords(List.of(alert));
                    requestedPage.setTotal(21L);
                    return requestedPage;
                });

        AlertPageResponse response = alertService.getAlerts(
                AlertStatus.OPEN,
                Severity.HIGH,
                "ACC-001",
                1,
                10
        );

        ArgumentCaptor<Page<Alert>> pageCaptor =
                ArgumentCaptor.forClass(Page.class);
        verify(alertMapper).selectPage(pageCaptor.capture(), any(Wrapper.class));

        // API 第 1 页对应 MyBatis-Plus 的第 2 页。
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10L);
        assertThat(response.getPage()).isEqualTo(1L);
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(21L);
        assertThat(response.getTotalPages()).isEqualTo(3L);
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
        command.setRelatedTransactionIds(List.of(5001L, 5002L, 5002L));
        return command;
    }

    private Alert openAlert() {
        Instant now = Instant.now();
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
