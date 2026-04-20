package ru.sbrf.uddk.ai.testing.ui.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса на улучшение кода
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeImprovementRequestDto {

    /**
     * Исходный код для улучшения
     */
    private String sourceCode;
}
