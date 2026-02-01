package ru.sbrf.uddk.ai.testing.entity;

import ru.sbrf.uddk.ai.testing.entity.consts.SessionStatus;
import ru.sbrf.uddk.ai.testing.entity.consts.TestGoal;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "test_sessions")
@Data
public class TestSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "target_url", nullable = false)
    private String targetUrl;

    @Column(name = "goal")
    @Enumerated(EnumType.STRING)
    private TestGoal goal;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private SessionStatus status;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("timestamp ASC")
    private List<AgentAction> actions = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DiscoveredIssue> discoveredIssues = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "session_observations", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "observation", length = 5000)
    private List<String> keyObservations = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "test_session_urls", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "url")
    private Set<String> visitedUrls = new HashSet<>();

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "summary", length = 5000)
    private String summary;

    @Column(name = "total_actions")
    private Integer totalActions;

    @Column(name = "successful_actions")
    private Integer successfulActions;

    @Column(name = "failed_actions")
    private Integer failedActions;

    public void addAction(AgentAction action) {
        action.setSession(this);
        this.actions.add(action);
    }

    public void addIssue(DiscoveredIssue issue) {
        issue.setSession(this);
        this.discoveredIssues.add(issue);
    }

    public void addObservation(String observation) {
        this.keyObservations.add(LocalDateTime.now() + ": " + observation);
    }

    public void markAsCompleted() {
        this.status = SessionStatus.COMPLETED;
        this.finishedAt = LocalDateTime.now();
        if (this.startedAt != null) {
            this.durationSeconds = Duration.between(startedAt, finishedAt).getSeconds();
        }

        // Подсчет статистики
        this.totalActions = actions.size();
        this.successfulActions = (int) actions.stream()
                .filter(a -> a.getResult() != null && Boolean.TRUE.equals(a.getResult().getSuccess()))
                .count();
        this.failedActions = totalActions - successfulActions;

        // Генерация сводки
        generateSummary();
    }

    private void generateSummary() {
        this.summary = String.format("""
                        Сессия тестирования завершена.
                        Цель: %s
                        URL: %s
                        Длительность: %d сек
                        Действий выполнено: %d
                        Успешных: %d
                        Неудачных: %d
                        Проблем обнаружено: %d
                        URL посещено: %d""",
                goal != null ? goal.getDescription() : "N/A",
                targetUrl,
                durationSeconds != null ? durationSeconds : 0,
                totalActions != null ? totalActions : 0,
                successfulActions != null ? successfulActions : 0,
                failedActions != null ? failedActions : 0,
                discoveredIssues.size(),
                visitedUrls.size()
        );
    }

    public List<AgentAction> getRecentActions(int count) {
        return actions.stream()
                .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                .limit(count)
                .collect(Collectors.toList());
    }
}
