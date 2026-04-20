package ru.sbrf.uddk.ai.testing.testgen.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Модель метода теста
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestMethod {

    /**
     * Имя метода
     */
    private String methodName;

    /**
     * Описание (для @DisplayName)
     */
    private String displayName;

    /**
     * Порядок выполнения (@Order)
     */
    private int order;

    /**
     * Код метода (тело)
     */
    private String body;

    /**
     * Ожидаемый результат (для ассертов)
     */
    private String expectedOutcome;

    /**
     * Типы ассертов для генерации
     */
    private List<Assertion> assertions = new ArrayList<>();

    /**
     * Параметры метода (для DataProvider)
     */
    private List<MethodParameter> parameters = new ArrayList<>();

    /**
     * Добавить ассерт
     */
    public void addAssertion(Assertion assertion) {
        this.assertions.add(assertion);
    }

    /**
     * Добавить параметр
     */
    public void addParameter(MethodParameter parameter) {
        this.parameters.add(parameter);
    }

    /**
     * Тип ассерта
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Assertion {
        private String type; // "assertEquals", "assertTrue", "assertFalse", "contains"
        private String actual; // что проверяем
        private String expected; // ожидаемое значение
        private String message; // сообщение об ошибке
    }

    /**
     * Параметр метода
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MethodParameter {
        private String name;
        private String type;
        private String value;
    }
}
