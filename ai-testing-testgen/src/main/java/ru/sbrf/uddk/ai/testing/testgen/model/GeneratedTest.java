package ru.sbrf.uddk.ai.testing.testgen.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Модель сгенерированного теста
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedTest {

    /**
     * Имя класса теста
     */
    private String className;

    /**
     * Пакет для класса
     */
    private String packageName;

    /**
     * Описание теста
     */
    private String description;

    /**
     * URL целевого приложения
     */
    private String targetUrl;

    /**
     * Методы теста
     */
    private List<TestMethod> testMethods = new ArrayList<>();

    /**
     * Импорты для генерации
     */
    private List<String> imports = new ArrayList<>();

    /**
     * Исходный код теста
     */
    private String sourceCode;

    /**
     * Добавить метод теста
     */
    public void addMethod(TestMethod method) {
        this.testMethods.add(method);
    }

    /**
     * Добавить импорт
     */
    public void addImport(String importStatement) {
        if (!this.imports.contains(importStatement)) {
            this.imports.add(importStatement);
        }
    }

    /**
     * Получить стандартные импорты для JUnit 5 + Selenium
     */
    public static List<String> getStandardImports() {
        List<String> imports = new ArrayList<>();
        imports.add("import org.junit.jupiter.api.*;");
        imports.add("import org.openqa.selenium.WebDriver;");
        imports.add("import org.openqa.selenium.WebElement;");
        imports.add("import org.openqa.selenium.chrome.ChromeDriver;");
        imports.add("import org.openqa.selenium.chrome.ChromeOptions;");
        imports.add("import org.openqa.selenium.support.ui.WebDriverWait;");
        imports.add("import org.openqa.selenium.support.ui.ExpectedConditions;");
        imports.add("import org.openqa.selenium.By;");
        imports.add("import java.time.Duration;");
        imports.add("import static org.junit.jupiter.api.Assertions.*;");
        return imports;
    }
}
