package com.lrj.end.service;

import com.lrj.end.entity.AiModelConfig;
import com.lrj.end.mapper.AiModelConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AiModelConfigService {
    private final AiModelConfigMapper modelConfigMapper;

    public AiModelConfigService(AiModelConfigMapper modelConfigMapper) {
        this.modelConfigMapper = modelConfigMapper;
    }

    @Transactional(readOnly = true)
    public List<AiModelConfig> listEnabledModels() {
        return modelConfigMapper.findEnabled();
    }
}
