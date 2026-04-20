package ru.sbrf.uddk.ai.testing.ui.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для ответа с улучшенным кодом
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
