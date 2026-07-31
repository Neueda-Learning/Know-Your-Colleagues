package com.example.knowyourcolleagues.service;

import com.example.knowyourcolleagues.bizexception.demo.DemoScenarioConflictException;
import com.example.knowyourcolleagues.bizexception.demo.DemoScenarioUnavailableException;
import com.example.knowyourcolleagues.dto.DemoScenarioStatusResponse;
import com.example.knowyourcolleagues.enums.DemoScenarioState;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * 以受控参数启动Dashboard模拟脚本，同一时间只允许一个脚本实例运行。
 */
@Service
@Slf4j
public class DemoScenarioService {

    private final Object lifecycleMonitor = new Object();
    private final DemoScenarioProcessLauncher processLauncher;
    private final boolean enabled;
    private final String nodeCommand;
    private final String configuredScriptPath;
    private final int transactionCount;
    private final long intervalMs;
    private final long statusTimeoutMs;

    private volatile Process runningProcess;
    private volatile DemoScenarioStatusResponse currentStatus;

    public DemoScenarioService(
            DemoScenarioProcessLauncher processLauncher,
            @Value("${demo.scenario.enabled:true}") boolean enabled,
            @Value("${demo.scenario.node-command:node}")
            String nodeCommand,
            @Value("${demo.scenario.script-path:"
                    + "scripts/mock-dashboard-scenario.mjs}")
            String configuredScriptPath,
            @Value("${demo.scenario.count:30}") int transactionCount,
            @Value("${demo.scenario.interval-ms:1000}") long intervalMs,
            @Value("${demo.scenario.status-timeout-ms:10000}")
            long statusTimeoutMs
    ) {
        this.processLauncher = processLauncher;
        this.enabled = enabled;
        this.nodeCommand = nodeCommand;
        this.configuredScriptPath = configuredScriptPath;
        this.transactionCount = transactionCount;
        this.intervalMs = intervalMs;
        this.statusTimeoutMs = statusTimeoutMs;
        this.currentStatus = initialStatus();
    }

    public DemoScenarioStatusResponse getStatus() {
        return currentStatus;
    }

    public DemoScenarioStatusResponse start(int backendPort) {
        synchronized (lifecycleMonitor) {
            validateConfiguration();
            if (currentStatus.state() == DemoScenarioState.RUNNING
                    && runningProcess != null
                    && runningProcess.isAlive()) {
                throw new DemoScenarioConflictException(
                        "A live demo scenario is already running"
                );
            }

            Path workingDirectory = Path.of(
                    System.getProperty("user.dir")
            ).toAbsolutePath().normalize();
            Path scriptPath = resolveScriptPath(workingDirectory);
            String backendUrl = "http://127.0.0.1:" + backendPort;
            List<String> command = buildCommand(scriptPath, backendUrl);

            Process process;
            Instant startedAt = Instant.now();
            try {
                process = processLauncher.start(
                        command,
                        workingDirectory
                );
            } catch (IOException exception) {
                currentStatus = new DemoScenarioStatusResponse(
                        DemoScenarioState.FAILED,
                        "Unable to start the live demo scenario",
                        transactionCount,
                        intervalMs,
                        startedAt,
                        Instant.now(),
                        null
                );
                throw new DemoScenarioUnavailableException(
                        "Unable to start the live demo scenario. "
                                + "Confirm that Node.js is available.",
                        exception
                );
            }

            runningProcess = process;
            currentStatus = new DemoScenarioStatusResponse(
                    DemoScenarioState.RUNNING,
                    "Creating demo transactions",
                    transactionCount,
                    intervalMs,
                    startedAt,
                    null,
                    null
            );

            Thread.ofVirtual()
                    .name("demo-scenario-output")
                    .start(() -> logOutput(process));
            process.onExit().whenComplete(
                    (completedProcess, error) ->
                            complete(process, error)
            );

            log.info(
                    "Started live demo scenario: count={}, intervalMs={}",
                    transactionCount,
                    intervalMs
            );
            return currentStatus;
        }
    }

    private DemoScenarioStatusResponse initialStatus() {
        if (!enabled) {
            return new DemoScenarioStatusResponse(
                    DemoScenarioState.DISABLED,
                    "Live demo execution is disabled",
                    transactionCount,
                    intervalMs,
                    null,
                    null,
                    null
            );
        }
        return new DemoScenarioStatusResponse(
                DemoScenarioState.IDLE,
                "Ready to run the live demo",
                transactionCount,
                intervalMs,
                null,
                null,
                null
        );
    }

    private void validateConfiguration() {
        if (!enabled) {
            throw new DemoScenarioUnavailableException(
                    "Live demo execution is disabled"
            );
        }
        if (transactionCount <= 0
                || intervalMs <= 0
                || statusTimeoutMs <= 0) {
            throw new DemoScenarioUnavailableException(
                    "Live demo configuration is invalid"
            );
        }
    }

    private Path resolveScriptPath(Path workingDirectory) {
        Path scriptPath = workingDirectory
                .resolve(configuredScriptPath)
                .normalize();
        if (!scriptPath.startsWith(workingDirectory)) {
            throw new DemoScenarioUnavailableException(
                    "The configured demo script must be inside the project"
            );
        }
        if (!Files.isRegularFile(scriptPath)) {
            throw new DemoScenarioUnavailableException(
                    "Demo script was not found at " + scriptPath
            );
        }
        return scriptPath;
    }

    private List<String> buildCommand(
            Path scriptPath,
            String backendUrl
    ) {
        return List.of(
                nodeCommand,
                scriptPath.toString(),
                "--base-url",
                backendUrl,
                "--count",
                String.valueOf(transactionCount),
                "--interval-ms",
                String.valueOf(intervalMs),
                "--status-timeout-ms",
                String.valueOf(statusTimeoutMs),
                "--no-dashboard-watch"
        );
    }

    private void logOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        process.getInputStream(),
                        StandardCharsets.UTF_8
                )
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[live-demo] {}", line);
            }
        } catch (IOException exception) {
            log.warn("Unable to read live demo output", exception);
        }
    }

    private void complete(Process process, Throwable error) {
        synchronized (lifecycleMonitor) {
            if (runningProcess != process) {
                return;
            }

            int exitCode = error == null ? process.exitValue() : -1;
            DemoScenarioState state = error == null && exitCode == 0
                    ? DemoScenarioState.COMPLETED
                    : DemoScenarioState.FAILED;
            String message = state == DemoScenarioState.COMPLETED
                    ? "Live demo completed successfully"
                    : "Live demo failed; check the backend log";
            currentStatus = new DemoScenarioStatusResponse(
                    state,
                    message,
                    transactionCount,
                    intervalMs,
                    currentStatus.startedAt(),
                    Instant.now(),
                    exitCode
            );
            runningProcess = null;
            log.info(
                    "Live demo scenario finished: state={}, exitCode={}",
                    state,
                    exitCode
            );
        }
    }

    @PreDestroy
    public void stopRunningProcess() {
        synchronized (lifecycleMonitor) {
            if (runningProcess != null && runningProcess.isAlive()) {
                runningProcess.destroy();
            }
        }
    }
}
