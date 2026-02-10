package ru.sbrf.uddk.ai.testing.ui.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderColumn;
import lombok.Data;
import ru.sbrf.uddk.ai.testing.entity.TestSession;
import ru.sbrf.uddk.ai.testing.entity.consts.IssueSeverity;
import ru.sbrf.uddk.ai.testing.entity.consts.IssueType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class RestDiscoveredIssue {
    private UUID id;
    private String title;
    private String description;
    private IssueSeverity severity;
    private IssueType type;
    private String screenshot;
    private String htmlContext;
    private String url;
    private String elementSelector;
    private List<String> reproductionSteps = new ArrayList<>();
    private Map<String, String> metadata = new HashMap<>();
    private LocalDateTime discoveredAt;
    private Boolean autoReported;
}
