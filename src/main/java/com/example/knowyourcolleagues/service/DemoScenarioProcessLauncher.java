package com.example.knowyourcolleagues.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 将进程创建与运行状态管理分离，便于测试并避免使用 shell。
 */
@Component
public class DemoScenarioProcessLauncher {

    public Process start(
            List<String> command,
            Path workingDirectory
    ) throws IOException {
        return new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
    }
}
