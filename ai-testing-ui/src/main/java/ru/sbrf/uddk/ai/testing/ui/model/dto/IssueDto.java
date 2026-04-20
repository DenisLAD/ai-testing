package ru.sbrf.uddk.ai.testing.ui.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * DTO для представления обнаруженной проблемы в REST API
 */
@Data
@Builder
public class IssueDto {
    
    private UUID id;
    private String type;
    private String severity;
    private String title;
    private String description;
    private String stepsToReproduce;
}
