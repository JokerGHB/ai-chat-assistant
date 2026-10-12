package com.lrj.end.mapper;

import com.lrj.end.entity.AiModelConfig;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

public interface AiModelConfigMapper {
    @Select("""
            SELECT id, name, provider, model_code, base_url, enabled, sort_order,
                   created_at, updated_at
            FROM ai_model_config
            WHERE enabled = 1
            ORDER BY sort_order ASC, id ASC
            """)
    List<AiModelConfig> findEnabled();

    @Select("""
            SELECT id, name, provider, model_code, base_url, enabled, sort_order,
                   created_at, updated_at
            FROM ai_model_config
            WHERE id = #{id} AND enabled = 1
            """)
    Optional<AiModelConfig> findEnabledById(@Param("id") Long id);
}
