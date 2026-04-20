package ru.sbrf.uddk.ai.testing.ui.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO для запроса на генерацию теста
 */
@Data
@Builder
public class GenerateTestRequestDto {
    
    private String outputPath;
    private boolean parameterized;
}
