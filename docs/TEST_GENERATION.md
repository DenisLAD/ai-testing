# Руководство по генерации автотестов

## Обзор

Модуль **ai-testing-testgen** позволяет генерировать исполняемые JUnit 5 + Selenium тесты на основе завершённых AI-сессий тестирования.

## Архитектура

```
ai-testing-testgen/
├── model/
│   ├── GeneratedTest.java      # Модель сгенерированного теста
│   ├── TestMethod.java         # Модель метода теста
│   └── TestScenario.java       # Модель тестового сценария
├── service/
│   └── TestGeneratorService.java  # Сервис генерации тестов
├── template/
│   └── TestTemplateEngine.java    # Шаблонизатор кода
└── rest/
    └── TestGenerationController.java  # REST API
```

## Возможности

### 1. Генерация обычных тестов

Генерирует последовательность методов JUnit 5 на основе действий AI:

```java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LoginInvalidCredentialsTest {
    
    private static WebDriver driver;
    private static WebDriverWait wait;
    
    @BeforeAll
    static void setUp() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }
    
    @Order(1)
    @Test
    @DisplayName("Навигация на страницу логина")
    void navigate_to_01_page() {
        driver.get("https://the-internet.herokuapp.com/login");
    }
    
    @Order(2)
    @Test
    @DisplayName("Ввод username")
    void type_02_username() {
        WebElement element = driver.findElement(By.id("username"));
        element.clear();
        element.sendKeys("invalid_user");
    }
}
```

### 2. Генерация параметризованных тестов

Создаёт тесты с `@ParameterizedTest` для множественных наборов данных:

```java
@ParameterizedTest
@CsvSource({
    "invalid_user,wrong_pass,Your password is invalid",
    "admin,wrong,Invalid credentials"
})
void testWithParameters(String username, String password, String expectedError) {
    // Тест с параметрами
}
```

### 3. Сохранение сценариев

Сохранение переиспользуемых сценариев в базу данных:

- **TestScenario** — сущность сценария
- **ScenarioAction** — действия сценария
- Поддержка параметризации
- Отслеживание истории выполнений

---

## REST API

### 1. Генерация теста по сессии

**Запрос:**
```bash
POST /test/{id}/generate-test
```

**Ответ:**
```json
{
  "className": "LoginInvalidCredentialsTest",
  "packageName": "ru.sbrf.uddk.ai.testing.generated",
  "description": "Тест логина с невалидными данными",
  "sourceCode": "package ru.sbrf.uddk.ai.testing.generated;\n...",
  "methodsCount": 5
}
```

### 2. Генерация параметризованного теста

**Запрос:**
```bash
POST /test/{id}/generate-parameterized-test
```

### 3. Скачать тест файлом

**Запрос:**
```bash
GET /test/{id}/test/download
```

**Ответ:** Файл `.java` для скачивания

### 4. Сохранить тест в директорию

**Запрос:**
```bash
POST /test/{id}/test/save
Content-Type: application/json

{
  "path": "/path/to/tests"
}
```

**Ответ:**
```json
{
  "filePath": "/path/to/tests/LoginInvalidCredentialsTest.java",
  "className": "LoginInvalidCredentialsTest"
}
```

### 5. Поиск похожих сценариев

**Запрос:**
```bash
GET /test/{id}/similar-scenarios
```

### 6. Получить хэш сценария

**Запрос:**
```bash
GET /test/{id}/scenario-hash
```

---

## Улучшенная подготовка DOM для LLM

### Проблемы старой версии

1. **Теги `<html>`, `<head>`, `<body>`** вводили LLM в заблуждение
2. **Много шума** — скрытые элементы, скрипты, стили
3. **Отсутствие маркеров** видимой области

### Новая реализация: `EnhancedDOMExtractor`

**Файл:** `ai-testing-core/src/main/java/ru/sbrf/uddk/ai/testing/utils/EnhancedDOMExtractor.java`

#### Особенности:

1. **Удаление служебных тегов:**
   ```java
   // Удаляем html, head, body теги
   result = result.replaceAll("(?i)<html[^>]*>", "");
   result = result.replaceAll("(?i)</html>", "");
   result = result.replaceAll("(?i)<head[^>]*>.*?</head>", "");
   result = result.replaceAll("(?i)<body[^>]*>", "");
   result = result.replaceAll("(?i)</body>", "");
   ```

2. **Извлечение только видимых элементов:**
   - Проверка `getBoundingClientRect()` для viewport
   - Проверка `window.getComputedStyle()` для visibility
   - Фильтрация `display:none`, `visibility:hidden`, `opacity:0`

3. **Фильтрация по важности:**
   - Интерактивные элементы (`a`, `button`, `input`, etc.)
   - Заголовки (`h1`-`h6`)
   - Элементы с текстовым контентом
   - Элементы с изображениями

4. **Маркеры видимой области:**
   ```
   <!-- VISIBLE DOM START -->
   ... элементы ...
   <!-- VISIBLE DOM END -->
   ```

5. **Компактное представление:**
   ```java
   // Метод extractCompactDOM() возвращает:
   <button id="login" class="btn"> "Войти" </button>
   <input type="text" placeholder="Username" />
   <a href="/dashboard" class="link"> "Dashboard" </a>
   ```

#### Использование:

```java
// В DecisionEngineService
String domForLLM = EnhancedDOMExtractor.extractForLLM(driver);

// Компактное представление
String compactDOM = EnhancedDOMExtractor.extractCompactDOM(driver);
```

---

## Новые сущности

### TestScenario

```java
@Entity
@Table(name = "test_scenarios")
public class TestScenario {
    private UUID id;
    private String name;              // "Логин с невалидными данными"
    private String description;
    private String scenarioHash;      // Уникальный хэш
    private String targetUrl;
    private TestGoal goal;
    private String preconditions;
    private String postconditions;
    private String parameters;        // JSON с параметрами
    private List<ScenarioAction> actions;
    private LocalDateTime createdAt;
    private LocalDateTime lastExecutedAt;
    private Integer executionCount;
}
```

### ScenarioAction

```java
@Entity
@Table(name = "scenario_actions")
public class ScenarioAction {
    private UUID id;
    private Integer stepOrder;
    private String actionType;        // CLICK, TYPE, etc.
    private String targetSelector;
    private String targetXpath;
    private String targetCss;
    private String inputValue;
    private String description;
    private String expectedOutcome;
    private Boolean isAssertion;
    private String assertionType;
    private String assertionExpected;
    private Boolean canBeParameterized;
    private String parameterName;
}
```

### Улучшенный AgentAction

Добавлены поля для генерации тестов:

```java
public class AgentAction {
    // ... существующие поля ...
    
    private String stepDescription;       // Описание шага
    private Boolean isAssertion;          // Является ли ассертом
    private String assertionType;         // Тип ассерта
    private String assertionExpected;     // Ожидаемое значение
    private Boolean canBeParameterized;   // Можно ли параметризовать
    private String parameterName;         // Имя параметра
    private Integer stepOrder;            // Порядок шага
}
```

---

## Примеры использования

### Пример 1: Генерация теста через REST

```bash
# 1. Завершите сессию тестирования
# Статус должен быть COMPLETED

# 2. Сгенерируйте тест
curl -X POST http://localhost:8080/test/550e8400-e29b-41d4-a716-446655440000/generate-test

# 3. Скачайте сгенерированный файл
curl -O http://localhost:8080/test/550e8400-e29b-41d4-a716-446655440000/test/download
```

### Пример 2: Генерация в коде

```java
@Autowired
private TestGeneratorService testGeneratorService;

public void generateTestForSession(UUID sessionId) {
    TestSession session = testSessionRepository.findById(sessionId)
        .orElseThrow();
    
    // Генерация обычного теста
    GeneratedTest test = testGeneratorService.generateTest(session);
    
    // Сохранение в файл
    Path savedPath = testGeneratorService.saveTestToFile(
        test, 
        "src/test/java"
    );
    
    System.out.println("Тест сохранён: " + savedPath);
}
```

### Пример 3: Параметризованный тест

```java
@Autowired
private TestGeneratorService testGeneratorService;

public void generateParameterizedTest(UUID sessionId) {
    TestSession session = testSessionRepository.findById(sessionId)
        .orElseThrow();
    
    GeneratedTest parameterizedTest = testGeneratorService
        .generateParameterizedTest(session);
    
    // Содержит @ParameterizedTest с @CsvSource
    System.out.println(parameterizedTest.getSourceCode());
}
```

---

## Типы генерируемых действий

| Действие AI | Генерируемый код |
|-------------|------------------|
| `NAVIGATE_TO` | `driver.get(url)` + `assertEquals(title)` |
| `CLICK` | `findElement()` + `click()` + `sleep()` |
| `TYPE` | `findElement()` + `clear()` + `sendKeys()` |
| `ASSERT_PRESENCE` | `findElement()` + `assertTrue(isDisplayed())` |
| `ASSERT_TEXT` | `findElement()` + `assertTrue(getText().contains())` |
| `SCROLL_DOWN` | `executeScript("window.scrollBy(0, 500)")` |
| `SCROLL_UP` | `executeScript("window.scrollBy(0, -500)")` |
| `REFRESH` | `driver.navigate().refresh()` |

---

## Настройка и сборка

### 1. Добавьте модуль в зависимости

```xml
<dependency>
    <groupId>ru.sbrf.uddk.ai.testing</groupId>
    <artifactId>ai-testing-testgen</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. Обновите pom.xml

Добавьте модуль в родительский `pom.xml`:

```xml
<modules>
    <module>ai-testing-core</module>
    <module>ai-testing-ui</module>
    <module>ai-testing-testgen</module>
    <module>ai-testing-app</module>
</modules>
```

### 3. Сборка

```bash
mvn clean install
```

---

## Структура сгенерированного теста

```java
package ru.sbrf.uddk.ai.testing.generated;

import org.junit.jupiter.api.*;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GeneratedTestNameTest {
    
    private static WebDriver driver;
    private static WebDriverWait wait;
    
    @BeforeAll
    static void setUp() {
        // Инициализация Chrome в headless режиме
    }
    
    @AfterAll
    static void tearDown() {
        // Закрытие браузера
    }
    
    @Order(1)
    @Test
    @DisplayName("Описание шага")
    void action_01_selector() {
        // Код действия
    }
    
    // ... остальные методы
}
```

---

## Ограничения и рекомендации

### Ограничения

1. **Только завершённые сессии** — статус должен быть `COMPLETED`
2. **Headless режим** — некоторые элементы могут отличаться
3. **Простые ассерты** — только базовые проверки
4. **Нет Page Object** — генерируется "плоский" тест

### Рекомендации

1. **Проверяйте сгенерированный код** перед запуском
2. **Добавляйте явные ожидания** вместо `Thread.sleep()`
3. **Рефакторьте в Page Object** для поддержки
4. **Используйте параметризацию** для похожих сценариев
5. **Сохраняйте сценарии** для переиспользования

---

## Roadmap улучшений

- [ ] Генерация Page Object классов
- [ ] Интеграция с Allure Reports
- [ ] Поддержка TestNG
- [ ] Генерация Cucumber сценариев (Gherkin)
- [ ] Экспорт в CI/CD (GitHub Actions, Jenkins)
- [ ] Версионирование сценариев
- [ ] AI-оптимизация тестов (удаление лишних шагов)
