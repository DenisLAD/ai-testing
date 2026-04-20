package ru.sbrf.uddk.ai.testing.ui.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO для представления действия агента в REST API
 */
@Data
@Builder
public class ActionDto {
    
    private UUID id;
    private String actionType;
    private String targetSelector;
    private String targetXpath;
    private String targetCss;
    private String inputValue;
    
    private String reason;
    private String expectedOutcome;
    private String stepDescription;
    
    private Boolean isAssertion;
    private String assertionType;
    private String assertionExpected;
    
    private Boolean success;
    private String resultMessage;

    private LocalDateTime timestamp;
    private Long executionTimeMs;

    // Скриншоты (base64)
    private String screenshotBefore;
    private String screenshotAfter;

    // Поле session намеренно исключено для избежания циклической ссылки
}
