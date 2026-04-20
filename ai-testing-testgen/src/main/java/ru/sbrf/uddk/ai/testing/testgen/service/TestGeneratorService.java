package ru.sbrf.uddk.ai.testing.testgen.service;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import ru.sbrf.uddk.ai.testing.entity.TestSession;
import ru.sbrf.uddk.ai.testing.testgen.model.GeneratedTest;
import ru.sbrf.uddk.ai.testing.testgen.model.TestMethod;
import ru.sbrf.uddk.ai.testing.testgen.template.TestTemplateEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис генерации тестов на основе сессий тестирования
 */
@Slf4j
@Service
public class TestGeneratorService {

    @Autowired
    private TestTemplateEngine templateEngine;

    /**
     * Генерирует JUnit 5 тест на основе завершённой сессии
     */
    public GeneratedTest generateTest(TestSession session) {
        log.info("Генерация теста для сессии: {}", session.getId());

        GeneratedTest test = new GeneratedTest();
        test.setClassName(generateClassName(session));
        test.setPackageName("ru.sbrf.uddk.ai.testing.generated");
        test.setDescription(session.getDescription() != null ? session.getDescription() : session.getName());
        test.setTargetUrl(session.getTargetUrl());

        // Добавляем стандартные импорты
        test.getImports().addAll(GeneratedTest.getStandardImports());

        // Генерируем методы теста на основе действий
        List<AgentAction> actions = session.getActions();
        int order = 1;

        // Группируем действия по логическим шагам
        List<TestMethod> methods = generateTestMethods(actions, order);
        for (TestMethod method : methods) {
            test.addMethod(method);
        }

        // Генерируем исходный код
        String sourceCode = templateEngine.generate(test);
        test.setSourceCode(sourceCode);

        log.info("Тест сгенерирован: {} методов", test.getTestMethods().size());
        return test;
    }

    /**
     * Генерирует параметризованный тест с несколькими наборами данных
     */
    public GeneratedTest generateParameterizedTest(TestSession session) {
        log.info("Генерация параметризованного теста для сессии: {}", session.getId());

        GeneratedTest test = new GeneratedTest();
        test.setClassName(generateClassName(session) + "Parameterized");
        test.setPackageName("ru.sbrf.uddk.ai.testing.generated");
        test.setDescription(session.getDescription() + " (параметризованный)");
        test.setTargetUrl(session.getTargetUrl());

        test.getImports().addAll(GeneratedTest.getStandardImports());
        test.addImport("import org.junit.jupiter.params.ParameterizedTest;");
        test.addImport("import org.junit.jupiter.params.provider.CsvSource;");

        // Генерируем параметризованный метод
        TestMethod method = generateParameterizedTestMethod(session.getActions());
        test.addMethod(method);

        String sourceCode = templateEngine.generate(test);
        test.setSourceCode(sourceCode);

        return test;
    }

    /**
     * Сохраняет сгенерированный тест в файл
     */
    public Path saveTestToFile(GeneratedTest test, String outputPath) throws Exception {
        Path path = Paths.get(outputPath, test.getClassName() + ".java");
        
        Files.createDirectories(path.getParent());
        
        Files.writeString(
            path, 
            test.getSourceCode(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        log.info("Тест сохранён: {}", path.toAbsolutePath());
        return path;
    }

    /**
     * Вычисляет хэш сценария для поиска дубликатов
     */
    public String calculateScenarioHash(TestSession session) {
        // Хэш на основе URL и последовательности действий
        StringBuilder sb = new StringBuilder();
        sb.append(session.getTargetUrl());
        
        for (AgentAction action : session.getActions()) {
            sb.append(action.getActionType());
            sb.append(action.getTargetSelector() != null ? action.getTargetSelector() : "");
            sb.append(action.getInputValue() != null ? action.getInputValue() : "");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(sb.toString().getBytes());
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            log.error("Ошибка вычисления хэша", e);
            return String.valueOf(Math.abs(sb.toString().hashCode()));
        }
    }

    /**
     * Генерирует имя класса на основе сессии
     */
    private String generateClassName(TestSession session) {
        String name = session.getName();
        
        if (name == null || name.isEmpty()) {
            name = "Test";
        }

        // Преобразуем в CamelCase
        String className = name
            .replaceAll("[^a-zA-Z0-9\\s]", "")
            .replaceAll("\\s+", " ")
            .trim();

        StringBuilder result = new StringBuilder();
        for (String word : className.split(" ")) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }

        result.append("Test");
        return result.toString();
    }

    /**
     * Генерирует методы теста на основе последовательности действий
     */
    private List<TestMethod> generateTestMethods(List<AgentAction> actions, int startOrder) {
        // Группируем действия по типам для создания логических шагов
        List<TestMethod> methods = new ArrayList<>();
        int order = startOrder;
        for (AgentAction action : actions) {
            methods.add(convertActionToMethod(action, order++));
        }
        return methods;
    }

    /**
     * Конвертирует действие в метод теста
     */
    private TestMethod convertActionToMethod(AgentAction action, int order) {
        TestMethod method = new TestMethod();
        method.setOrder(order);
        method.setMethodName(generateMethodName(action, order));
        method.setDisplayName(generateDisplayName(action));
        method.setExpectedOutcome(action.getExpectedOutcome());

        // Генерируем тело метода на основе типа действия
        String body = generateMethodBody(action);
        method.setBody(body);

        // Добавляем ассерты если действие - это проверка
        if (action.getActionType().contains("ASSERT")) {
            TestMethod.Assertion assertion = new TestMethod.Assertion();
            assertion.setType("assertTrue");
            assertion.setActual("element.isDisplayed()");
            assertion.setExpected("true");
            assertion.setMessage("Элемент должен быть виден");
            method.addAssertion(assertion);
        }

        return method;
    }

    /**
     * Генерирует параметризованный метод теста
     */
    private TestMethod generateParameterizedTestMethod(List<AgentAction> actions) {
        TestMethod method = new TestMethod();
        method.setMethodName("testWithParameters");
        method.setDisplayName("Параметризованный тест");
        method.setOrder(1);

        // Добавляем параметры
        method.addParameter(new TestMethod.MethodParameter("username", "String", "{username}"));
        method.addParameter(new TestMethod.MethodParameter("password", "String", "{password}"));
        method.addParameter(new TestMethod.MethodParameter("expectedError", "String", "{expectedError}"));

        // Генерируем тело с параметрами
        StringBuilder body = new StringBuilder();
        body.append("// Навигация\n");
        body.append("driver.get(\"").append(actions.isEmpty() ? "" : "URL").append("\");\n\n");
        body.append("// Ввод параметров\n");
        body.append("WebElement usernameField = driver.findElement(By.id(\"username\"));\n");
        body.append("usernameField.clear();\n");
        body.append("usernameField.sendKeys(username);\n\n");
        body.append("WebElement passwordField = driver.findElement(By.id(\"password\"));\n");
        body.append("passwordField.clear();\n");
        body.append("passwordField.sendKeys(password);\n\n");
        body.append("// Клик на кнопку входа\n");
        body.append("WebElement loginButton = driver.findElement(By.className(\"radius\"));\n");
        body.append("loginButton.click();\n\n");
        body.append("// Проверка ошибки\n");
        body.append("WebElement errorMessage = wait.until(\n");
        body.append("    ExpectedConditions.visibilityOfElementLocated(By.id(\"flash\"))\n");
        body.append(");\n");
        body.append("assertTrue(errorMessage.getText().contains(expectedError));\n");

        method.setBody(body.toString());

        // Добавляем аннотацию CsvSource с данными
        method.setDisplayName("@ParameterizedTest\n@CsvSource({\n    \"invalid_user,wrong_pass,Your password is invalid\",\n    \"admin,wrong,Invalid credentials\",\n    \"user,user123,Login successful\"\n})");

        return method;
    }

    /**
     * Генерирует имя метода на основе действия
     */
    private String generateMethodName(AgentAction action, int order) {
        String actionType = action.getActionType().toLowerCase();
        String target = action.getTargetSelector() != null ? 
            action.getTargetSelector().replaceAll("[^a-zA-Z0-9]", "_") : "element";
        
        return String.format("%s_%02d_%s", actionType, order, truncate(target, 30));
    }

    /**
     * Генерирует отображаемое имя для метода
     */
    private String generateDisplayName(AgentAction action) {
        return switch (action.getActionType()) {
            case "NAVIGATE_TO" -> "Навигация на страницу";
            case "CLICK" -> "Клик на элемент: " + truncate(action.getTargetSelector(), 40);
            case "TYPE" -> "Ввод текста в поле: " + truncate(action.getTargetSelector(), 40);
            case "ASSERT_PRESENCE" -> "Проверка наличия элемента";
            case "ASSERT_TEXT" -> "Проверка текста элемента";
            case "SCROLL_DOWN" -> "Прокрутка вниз";
            case "SCROLL_UP" -> "Прокрутка вверх";
            case "REFRESH" -> "Обновление страницы";
            case "COMPLETE" -> "Завершение теста";
            default -> action.getActionType() + ": " + truncate(action.getTargetSelector(), 30);
        };
    }

    /**
     * Генерирует тело метода на основе действия
     */
    private String generateMethodBody(AgentAction action) {
        return switch (action.getActionType()) {
            case "NAVIGATE_TO" -> generateNavigateBody(action);
            case "CLICK" -> generateClickBody(action);
            case "TYPE" -> generateTypeBody(action);
            case "ASSERT_PRESENCE" -> generateAssertPresenceBody(action);
            case "ASSERT_TEXT" -> generateAssertTextBody(action);
            case "SCROLL_DOWN" -> generateScrollBody("down");
            case "SCROLL_UP" -> generateScrollBody("up");
            case "REFRESH" -> "driver.navigate().refresh();";
            case "NAVIGATE_BACK" -> "driver.navigate().back();";
            case "NAVIGATE_FORWARD" -> "driver.navigate().forward();";
            default -> "// Действие " + action.getActionType() + " не поддерживается";
        };
    }

    private String generateNavigateBody(AgentAction action) {
        return String.format("""
            driver.get("%s");
            assertEquals("%s", driver.getTitle());
            """, 
            action.getTargetSelector() != null ? action.getTargetSelector() : "",
            action.getExpectedOutcome() != null ? action.getExpectedOutcome() : "")
        .trim();
    }

    private String generateClickBody(AgentAction action) {
        String selector = action.getTargetSelector() != null ? action.getTargetSelector() : "element";
        return String.format("""
            WebElement element = driver.findElement(By.cssSelector("%s"));
            assertTrue(element.isDisplayed());
            element.click();
            Thread.sleep(1000); // Ожидание после клика
            """, selector).trim();
    }

    private String generateTypeBody(AgentAction action) {
        String selector = action.getTargetSelector() != null ? action.getTargetSelector() : "element";
        String value = action.getInputValue() != null ? action.getInputValue() : "test";
        return String.format("""
            WebElement element = driver.findElement(By.cssSelector("%s"));
            element.clear();
            element.sendKeys("%s");
            assertEquals("%s", element.getAttribute("value"));
            """, selector, value, value).trim();
    }

    private String generateAssertPresenceBody(AgentAction action) {
        String selector = action.getTargetSelector() != null ? action.getTargetSelector() : "element";
        return String.format("""
            WebElement element = driver.findElement(By.cssSelector("%s"));
            assertTrue(element.isDisplayed(), "Элемент должен быть на странице");
            """, selector).trim();
    }

    private String generateAssertTextBody(AgentAction action) {
        String selector = action.getTargetSelector() != null ? action.getTargetSelector() : "element";
        String expectedText = action.getInputValue() != null ? action.getInputValue() : "expected text";
        return String.format("""
            WebElement element = driver.findElement(By.cssSelector("%s"));
            assertTrue(element.getText().contains("%s"), "Текст должен содержать ожидаемое значение");
            """, selector, expectedText).trim();
    }

    private String generateScrollBody(String direction) {
        return String.format("""
            ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, %s500);");
            Thread.sleep(500);
            """, direction.equals("up") ? "-" : "").trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength);
    }
}
