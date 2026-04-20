package ru.sbrf.uddk.ai.testing.ui.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO для представления сессии тестирования в REST API
 */
@Data
@Builder
public class TestSessionDto {
    
    private UUID id;
    private String name;
    private String description;
    private String targetUrl;
    private String goal;
    private String status;
    
    private List<ActionDto> actions;
    private List<IssueDto> issues;
    
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationSeconds;
    private String summary;
    
    private Integer totalActions;
    private Integer successfulActions;
    private Integer failedActions;
}
