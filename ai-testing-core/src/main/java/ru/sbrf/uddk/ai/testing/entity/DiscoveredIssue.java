package ru.sbrf.uddk.ai.testing.entity;

import ru.sbrf.uddk.ai.testing.entity.consts.IssueSeverity;
import ru.sbrf.uddk.ai.testing.entity.consts.IssueType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "discovered_issues")
@Data
public class DiscoveredIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private TestSession session;

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private IssueSeverity severity;

    @Enumerated(EnumType.STRING)
    private IssueType type;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String screenshot;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String htmlContext;

    private String url;
    private String elementSelector;

    @ElementCollection
    @CollectionTable(name = "issue_steps", joinColumns = @JoinColumn(name = "issue_id"))
    @OrderColumn(name = "step_order")
    @Column(name = "step", length = 2000)
    private List<String> reproductionSteps = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "issue_metadata", joinColumns = @JoinColumn(name = "issue_id"))
    @MapKeyColumn(name = "skey")
    @Column(name = "svalue")
    private Map<String, String> metadata = new HashMap<>();

    private LocalDateTime discoveredAt;
    private Boolean autoReported;

    public void addReproductionStep(String step) {
        this.reproductionSteps.add(step);
    }

    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
    }
}