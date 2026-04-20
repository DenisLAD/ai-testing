package ru.sbrf.uddk.ai.testing.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "agent_actions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private TestSession session;

    @Column(nullable = false)
    private String actionType;

    private String targetElement;
    private String targetSelector;
    private String targetXpath;
    private String targetCss;
    private String inputValue;

    @Column(length = 1000)
    private String reason;

    @Column(length = 1000)
    private String expectedOutcome;

    /**
     * Описание шага для генерации теста
     */
    @Column(name = "step_description", length = 1000)
    private String stepDescription;

    /**
     * Является ли действие ассертом
     */
    @Column(name = "is_assertion")
    private Boolean isAssertion = false;

    /**
     * Тип ассерта (ASSERT_TEXT, ASSERT_PRESENCE, etc.)
     */
    @Column(name = "assertion_type", length = 50)
    private String assertionType;

    /**
     * Ожидаемое значение для ассерта
     */
    @Column(name = "assertion_expected", length = 2000)
    private String assertionExpected;

    /**
     * Можно ли параметризовать это действие
     */
    @Column(name = "can_be_parameterized")
    private Boolean canBeParameterized = false;

    /**
     * Имя параметра для DataProvider
     */
    @Column(name = "parameter_name", length = 100)
    private String parameterName;

    /**
     * Порядок шага в сценарии
     */
    @Column(name = "step_order")
    private Integer stepOrder;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String screenshotBefore;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String screenshotAfter;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String domSnapshotBefore;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String domSnapshotAfter;

    @ElementCollection
    @CollectionTable(name = "action_observations", joinColumns = @JoinColumn(name = "action_id"))
    @Column(name = "observation", length = 2000)
    private List<String> observations = new ArrayList<>();

    @Embedded
    private ActionResult result;

    @ElementCollection
    @CollectionTable(name = "action_ai_decisions", joinColumns = @JoinColumn(name = "action_id"))
    @Column(name = "decision", length = 2000)
    private List<String> aiDecisions = new ArrayList<>();

    private LocalDateTime timestamp;
    private Long executionTimeMs;

    public void addObservation(String observation) {
        this.observations.add(LocalDateTime.now() + ": " + observation);
    }

    public void addAiDecision(String decision) {
        this.aiDecisions.add(decision);
    }
}