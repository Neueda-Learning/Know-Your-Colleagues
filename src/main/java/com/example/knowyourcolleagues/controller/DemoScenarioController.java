package com.example.knowyourcolleagues.controller;

import com.example.knowyourcolleagues.bizexception.demo.DemoScenarioAccessDeniedException;
import com.example.knowyourcolleagues.dto.DemoScenarioStatusResponse;
import com.example.knowyourcolleagues.service.DemoScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;

@RestController
@RequestMapping("/api/demo/scenario")
@Tag(
        name = "Live Demo",
        description = "Starts and monitors the fixed dashboard demo scenario"
)
public class DemoScenarioController {

    private final DemoScenarioService demoScenarioService;
    private final boolean localOnly;

    public DemoScenarioController(
            DemoScenarioService demoScenarioService,
            @Value("${demo.scenario.local-only:true}") boolean localOnly
    ) {
        this.demoScenarioService = demoScenarioService;
        this.localOnly = localOnly;
    }

    @GetMapping
    @Operation(summary = "Get the current live-demo scenario status")
    public ResponseEntity<DemoScenarioStatusResponse> getStatus() {
        return ResponseEntity.ok(demoScenarioService.getStatus());
    }

    @PostMapping
    @Operation(summary = "Start the fixed live-demo transaction scenario")
    public ResponseEntity<DemoScenarioStatusResponse> start(
            HttpServletRequest request
    ) {
        if (localOnly && !isLoopbackAddress(request.getRemoteAddr())) {
            throw new DemoScenarioAccessDeniedException(
                    "The live demo can only be started from this machine"
            );
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                demoScenarioService.start(request.getLocalPort())
        );
    }

    private boolean isLoopbackAddress(String address) {
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
