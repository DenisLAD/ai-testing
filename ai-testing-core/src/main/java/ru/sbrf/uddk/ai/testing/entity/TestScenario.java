package ru.sbrf.uddk.ai.testing.entity;

import ru.sbrf.uddk.ai.testing.entity.consts.TestGoal;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сущность тестового сценария
 * Используется для хранения переиспользуемых сценариев тестирования
 */
@Entity
@Table(name = "test_scenarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Имя сценария (например, "Логин с невалидными данными")
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Описание сценария
     */
    @Column(name = "description", length = 2000)
    private String description;

    /**
     * Хэш сценария для поиска дубликатов
     */
    @Column(name = "scenario_hash", length = 64, unique = true)
    private String scenarioHash;

    /**
     * Целевой URL приложения
     */
    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    /**
     * Цель тестирования
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "goal")
    private TestGoal goal;

    /**
     * Предусловия (что должно быть до теста)
     */
    @Column(name = "preconditions", length = 2000)
    private String preconditions;

    /**
     * Постусловия (что должно быть после теста)
     */
    @Column(name = "postconditions", length = 2000)
    private String postconditions;

    /**
     * Параметры для параметризации (JSON)
     */
    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters;

    /**
     * Действия сценария
     */
    @OneToMany(mappedBy = "scenario", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("stepOrder ASC")
    private List<ScenarioAction> actions = new ArrayList<>();

    /**
     * Дата создания
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Дата последнего выполнения
     */
    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    /**
     * Количество выполнений
     */
    @Column(name = "execution_count")
    private Integer executionCount = 0;

    /**
     * Добавить действие
     */
    public void addAction(ScenarioAction action) {
        action.setScenario(this);
        this.actions.add(action);
    }

    /**
     * Обновить статистику выполнения
     */
    public void markAsExecuted() {
        this.lastExecutedAt = LocalDateTime.now();
        this.executionCount = (this.executionCount != null ? this.executionCount : 0) + 1;
    }
}
