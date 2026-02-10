package ru.sbrf.uddk.ai.testing.ui.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.Data;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import ru.sbrf.uddk.ai.testing.entity.DiscoveredIssue;
import ru.sbrf.uddk.ai.testing.entity.consts.SessionStatus;
import ru.sbrf.uddk.ai.testing.entity.consts.TestGoal;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
public class RestTestSession {
    private UUID id;
    private String name;
    private String description;
    private String targetUrl;
    private TestGoal goal;
    private SessionStatus status;
    private List<RestAgentAction> actions = new ArrayList<>();
    private List<RestDiscoveredIssue> discoveredIssues = new ArrayList<>();
    private List<String> keyObservations = new ArrayList<>();
    private Set<String> visitedUrls = new HashSet<>();
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationSeconds;
    private String summary;
    private Integer totalActions;
    private Integer successfulActions;
    private Integer failedActions;
}
