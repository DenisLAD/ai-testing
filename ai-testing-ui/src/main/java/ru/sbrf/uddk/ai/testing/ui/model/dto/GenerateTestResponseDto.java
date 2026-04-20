package ru.sbrf.uddk.ai.testing.ui.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO для ответа с генерацией теста
 */
@Data
@Builder
public class GenerateTestResponseDto {
    
    private String className;
    private String packageName;
    private String description;
    private String sourceCode;
    private Integer methodsCount;
    private String filePath;
}
