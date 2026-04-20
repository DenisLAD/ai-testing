package ru.sbrf.uddk.ai.testing.ui.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO для ответа с улучшенным кодом
 */
@Data
@Builder
public class CodeImprovementResponseDto {
    
    /**
     * Улучшенный код
     */
    private String improvedCode;
    
    /**
     * Описание изменений
     */
    private String improvementNotes;
}
