package ru.sbrf.uddk.ai.testing.ui.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import ru.sbrf.uddk.ai.testing.entity.ActionResult;
import ru.sbrf.uddk.ai.testing.entity.TestSession;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class RestAgentAction {
    private UUID id;
    private String actionType;
    private String targetElement;
    private String targetSelector;
    private String inputValue;
    private String reason;
    private String expectedOutcome;
    private String screenshotBefore;
    private String screenshotAfter;
    private String domSnapshotBefore;
    private String domSnapshotAfter;
    private List<String> observations = new ArrayList<>();
    private ActionResult result;
    private List<String> aiDecisions = new ArrayList<>();
    private LocalDateTime timestamp;
    private Long executionTimeMs;
}
