package ru.sbrf.uddk.ai.testing.ui.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO для запроса на улучшение кода
 */
@Data
@Builder
public class CodeImprovementRequestDto {
    
    /**
     * Исходный код для улучшения
     */
    private String sourceCode;
}
