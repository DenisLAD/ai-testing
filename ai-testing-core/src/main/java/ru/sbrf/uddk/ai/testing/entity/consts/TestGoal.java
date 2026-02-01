package ru.sbrf.uddk.ai.testing.entity.consts;

public enum TestGoal {
    EXPLORATORY("Исследовательское тестирование"),
    REGRESSION("Регрессионное тестирование"),
    SECURITY("Проверка безопасности"),
    PERFORMANCE("Тестирование производительности"),
    ACCESSIBILITY("Проверка доступности"),
    USER_JOURNEY("Тестирование пользовательского сценария");

    private final String description;

    TestGoal(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public String getPromptTemplate() {
        return switch (this) {
            case EXPLORATORY -> """
                Исследуй приложение как любопытный пользователь.
                Ищи неочевидные баги, проблемы UX, странное поведение.
                Особое внимание удели граничным случаям.
                """;
            case REGRESSION -> """
                Проверь основные функции приложения.
                Убедись, что ничего не сломалось.
                Сфокусируйся на критичных user journeys.
                """;
            case SECURITY -> """
                Ищи уязвимости безопасности: XSS, CSRF, инъекции.
                Проверяй авторизацию, доступ к данным.
                Тестируй граничные значения полей ввода.
                """;
            default -> "Тестируй приложение комплексно";
        };
    }
}