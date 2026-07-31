package com.example.knowyourcolleagues.demo;

import com.example.knowyourcolleagues.bizexception.demo.DemoScenarioConflictException;
import com.example.knowyourcolleagues.bizexception.demo.DemoScenarioUnavailableException;
import com.example.knowyourcolleagues.dto.DemoScenarioStatusResponse;
import com.example.knowyourcolleagues.enums.DemoScenarioState;
import com.example.knowyourcolleagues.service.DemoScenarioProcessLauncher;
import com.example.knowyourcolleagues.service.DemoScenarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoScenarioServiceTest {

    @Mock
    private DemoScenarioProcessLauncher processLauncher;

    @Mock
    private Process process;

    private CompletableFuture<Process> exitFuture;
    private DemoScenarioService service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        exitFuture = new CompletableFuture<>();
        when(processLauncher.start(any(), any())).thenReturn(process);
        when(process.isAlive()).thenReturn(true);
        when(process.getInputStream())
                .thenReturn(InputStream.nullInputStream());
        when(process.onExit()).thenReturn(exitFuture);

        service = new DemoScenarioService(
                processLauncher,
                true,
                "node",
                "scripts/mock-dashboard-scenario.mjs",
                12,
                500,
                8000
        );
    }

    @Test
    void shouldStartFixedScenarioCommand() throws Exception {
        DemoScenarioStatusResponse status = service.start(8080);

        assertThat(status.state()).isEqualTo(DemoScenarioState.RUNNING);
        assertThat(status.transactionCount()).isEqualTo(12);
        assertThat(status.intervalMs()).isEqualTo(500);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> commandCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(processLauncher).start(
                commandCaptor.capture(),
                any(Path.class)
        );
        assertThat(commandCaptor.getValue())
                .containsSubsequence(
                        "node",
                        Path.of(
                                System.getProperty("user.dir"),
                                "scripts/mock-dashboard-scenario.mjs"
                        ).toAbsolutePath().normalize().toString(),
                        "--base-url",
                        "http://127.0.0.1:8080",
                        "--count",
                        "12",
                        "--interval-ms",
                        "500"
                )
                .contains("--no-dashboard-watch");
    }

    @Test
    void shouldRejectSecondScenarioWhileOneIsRunning() {
        service.start(8080);

        assertThatThrownBy(() -> service.start(8080))
                .isInstanceOf(DemoScenarioConflictException.class)
                .hasMessageContaining("already running");
    }

    @Test
    void shouldRecordSuccessfulCompletion() {
        service.start(8080);
        when(process.exitValue()).thenReturn(0);

        exitFuture.complete(process);

        assertThat(service.getStatus().state())
                .isEqualTo(DemoScenarioState.COMPLETED);
        assertThat(service.getStatus().exitCode()).isZero();
        assertThat(service.getStatus().finishedAt()).isNotNull();
    }

    @Test
    void shouldRejectStartWhenExecutionIsDisabled() {
        DemoScenarioService disabledService = new DemoScenarioService(
                processLauncher,
                false,
                "node",
                "scripts/mock-dashboard-scenario.mjs",
                12,
                500,
                8000
        );

        assertThatThrownBy(() -> disabledService.start(8080))
                .isInstanceOf(DemoScenarioUnavailableException.class)
                .hasMessageContaining("disabled");
    }
}
