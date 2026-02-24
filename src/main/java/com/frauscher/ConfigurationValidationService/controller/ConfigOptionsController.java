package com.frauscher.ConfigurationValidationService.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.frauscher.ConfigurationValidationService.model.ConfigOptions;
import com.frauscher.ConfigurationValidationService.service.ConfigOptionsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/configoptions")
@RequiredArgsConstructor
public class ConfigOptionsController {

    private final ConfigOptionsService configOptionsService;

    @GetMapping
    public ResponseEntity<List<ConfigOptions>> getConfigOptions() {
        List<ConfigOptions> options = configOptionsService.getConfigOptions();
        return ResponseEntity.ok(options);
    }
}
