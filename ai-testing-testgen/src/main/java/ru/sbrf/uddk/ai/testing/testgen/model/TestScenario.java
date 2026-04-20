package ru.sbrf.uddk.ai.testing.testgen.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Модель сущности тестового сценария
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestScenario {

    /**
     * Уникальный идентификатор сценария
     */
    private String id;

    /**
     * Имя сценария (например, "Логин с невалидными данными")
     */
    private String name;

    /**
     * Описание сценария
     */
    private String description;

    /**
     * Хэш сценария для поиска дубликатов
     */
    private String scenarioHash;

    /**
     * Целевой URL
     */
    private String targetUrl;

    /**
     * Цель тестирования
     */
    private String goal;

    /**
     * Предусловия (что должно быть до теста)
     */
    private String preconditions;

    /**
     * Постусловия (что должно быть после теста)
     */
    private String postconditions;

    /**
     * Параметры для параметризации
     */
    private String parameters;

    /**
     * Дата создания
     */
    private String createdAt;

    /**
     * Дата последнего выполнения
     */
    private String lastExecutedAt;

    /**
     * Количество выполнений
     */
    private Integer executionCount;
}
