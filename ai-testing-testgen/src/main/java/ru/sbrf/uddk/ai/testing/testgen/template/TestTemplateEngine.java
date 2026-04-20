package ru.sbrf.uddk.ai.testing.testgen.template;

import ru.sbrf.uddk.ai.testing.testgen.model.GeneratedTest;
import ru.sbrf.uddk.ai.testing.testgen.model.TestMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Шаблонизатор для генерации исходного кода тестов
 */
@Slf4j
@Component
public class TestTemplateEngine {

    /**
     * Генерирует исходный код теста на основе модели
     */
    public String generate(GeneratedTest test) {
        StringBuilder sb = new StringBuilder();

        // Package declaration
        if (test.getPackageName() != null && !test.getPackageName().isEmpty()) {
            sb.append("package ").append(test.getPackageName()).append(";\n\n");
        }

        // Imports
        for (String importStmt : test.getImports()) {
            sb.append(importStmt).append("\n");
        }
        sb.append("\n");

        // Class declaration
        sb.append("/**\n");
        sb.append(" * ").append(test.getDescription() != null ? test.getDescription() : "Сгенерированный тест").append("\n");
        sb.append(" * URL: ").append(test.getTargetUrl()).append("\n");
        sb.append(" */\n");
        sb.append("@TestMethodOrder(MethodOrderer.OrderAnnotation.class)\n");
        sb.append("public class ").append(test.getClassName()).append(" {\n\n");

        // Fields
        sb.append("    private static WebDriver driver;\n");
        sb.append("    private static WebDriverWait wait;\n\n");

        // setUp method
        sb.append("    @BeforeAll\n");
        sb.append("    static void setUp() {\n");
        sb.append("        ChromeOptions options = new ChromeOptions();\n");
        sb.append("        options.addArguments(\"--headless=new\");\n");
        sb.append("        options.addArguments(\"--no-sandbox\");\n");
        sb.append("        options.addArguments(\"--disable-dev-shm-usage\");\n");
        sb.append("        driver = new ChromeDriver(options);\n");
        sb.append("        wait = new WebDriverWait(driver, Duration.ofSeconds(10));\n");
        sb.append("        driver.manage().window().maximize();\n");
        sb.append("    }\n\n");

        // tearDown method
        sb.append("    @AfterAll\n");
        sb.append("    static void tearDown() {\n");
        sb.append("        if (driver != null) {\n");
        sb.append("            driver.quit();\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        // Test methods
        for (TestMethod method : test.getTestMethods()) {
            sb.append(generateMethod(method));
            sb.append("\n");
        }

        // Close class
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Генерирует код метода
     */
    private String generateMethod(TestMethod method) {
        StringBuilder sb = new StringBuilder();

        // Order annotation
        sb.append("    @Order(").append(method.getOrder()).append(")\n");

        // DisplayName annotation
        if (method.getDisplayName() != null && !method.getDisplayName().startsWith("@")) {
            sb.append("    @DisplayName(\"").append(escapeQuotes(method.getDisplayName())).append("\")\n");
        }

        // Test annotation
        if (method.getDisplayName() != null && method.getDisplayName().startsWith("@ParameterizedTest")) {
            sb.append("    ").append(method.getDisplayName()).append("\n");
        } else {
            sb.append("    @Test\n");
        }

        // Method signature
        if (method.getParameters().isEmpty()) {
            sb.append("    void ").append(method.getMethodName()).append("() {\n");
        } else {
            sb.append("    void ").append(method.getMethodName())
              .append("(")
              .append(method.getParameters().stream()
                  .map(p -> p.getType() + " " + p.getName())
                  .collect(Collectors.joining(", ")))
              .append(") {\n");
        }

        // Method body
        if (method.getBody() != null && !method.getBody().isEmpty()) {
            String[] lines = method.getBody().split("\n");
            for (String line : lines) {
                sb.append("        ").append(line.trim()).append("\n");
            }
        } else {
            sb.append("        // TODO: Implement test logic\n");
        }

        // Assertions
        for (TestMethod.Assertion assertion : method.getAssertions()) {
            sb.append(generateAssertion(assertion));
        }

        // Close method
        sb.append("    }\n\n");

        return sb.toString();
    }

    /**
     * Генерирует код ассерта
     */
    private String generateAssertion(TestMethod.Assertion assertion) {
        return switch (assertion.getType()) {
            case "assertEquals" -> String.format(
                "        assertEquals(%s, %s, \"%s\");\n",
                escapeQuotes(assertion.getExpected()),
                assertion.getActual(),
                escapeQuotes(assertion.getMessage())
            );
            case "assertTrue" -> String.format(
                "        assertTrue(%s, \"%s\");\n",
                assertion.getActual(),
                escapeQuotes(assertion.getMessage())
            );
            case "assertFalse" -> String.format(
                "        assertFalse(%s, \"%s\");\n",
                assertion.getActual(),
                escapeQuotes(assertion.getMessage())
            );
            case "assertNotNull" -> String.format(
                "        assertNotNull(%s, \"%s\");\n",
                assertion.getActual(),
                escapeQuotes(assertion.getMessage())
            );
            case "contains" -> String.format(
                "        assertTrue(%s.contains(\"%s\"), \"%s\");\n",
                assertion.getActual(),
                escapeQuotes(assertion.getExpected()),
                escapeQuotes(assertion.getMessage())
            );
            default -> "        // Unknown assertion type: " + assertion.getType() + "\n";
        };
    }

    /**
     * Экранирует кавычки в строке
     */
    private String escapeQuotes(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
