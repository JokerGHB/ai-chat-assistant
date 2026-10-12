package com.lrj.end.controller;

import com.lrj.end.entity.AiModelConfig;
import com.lrj.end.service.AiModelConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/models")
public class AiModelConfigController {
    private final AiModelConfigService modelConfigService;

    public AiModelConfigController(AiModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    @GetMapping
    public List<AiModelConfig> listEnabledModels() {
        return modelConfigService.listEnabledModels();
    }
}
