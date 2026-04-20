package ru.sbrf.uddk.ai.testing.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Действие в тестовом сценарии
 * Упрощённая версия AgentAction для хранения сценариев
 */
@Entity
@Table(name = "scenario_actions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id")
    private TestScenario scenario;

    /**
     * Порядок шага в сценарии
     */
    @Column(name = "step_order")
    private Integer stepOrder;

    /**
     * Тип действия (CLICK, TYPE, NAVIGATE_TO, etc.)
     */
    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    /**
     * Селектор целевого элемента
     */
    @Column(name = "target_selector", length = 1000)
    private String targetSelector;

    /**
     * XPath элемента (альтернативный селектор)
     */
    @Column(name = "target_xpath", length = 1000)
    private String targetXpath;

    /**
     * CSS селектор элемента
     */
    @Column(name = "target_css", length = 1000)
    private String targetCss;

    /**
     * Входное значение (для TYPE)
     */
    @Column(name = "input_value", length = 2000)
    private String inputValue;

    /**
     * Описание шага
     */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Ожидаемый результат
     */
    @Column(name = "expected_outcome", length = 1000)
    private String expectedOutcome;

    /**
     * Является ли это действие ассертом
     */
    @Column(name = "is_assertion")
    private Boolean isAssertion = false;

    /**
     * Тип ассерта (для проверок)
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
     * Имя параметра (если параметризуется)
     */
    @Column(name = "parameter_name", length = 100)
    private String parameterName;

    /**
     * Комментарий к шагу
     */
    @Column(name = "comment", length = 500)
    private String comment;

    /**
     * Дата создания
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
