# AI Testing Platform

Платформа для **автономного AI-тестирования веб-приложений**. Система использует искусственный интеллект (LLM) для автоматического исследования веб-интерфейсов, обнаружения проблем и **генерации автотестов на Java**.

## 📑 Оглавление

- [Возможности](#возможности)
- [Быстрый старт](#быстрый-старт)
- [Технологии](#технологии)
- [Архитектура](#архитектура)
- [Действия агента](#действия-агента)
- [Генерация автотестов](#генерация-автотестов)
- [Улучшения (Декабрь 2026)]#улучшения-декабрь-2026)
- [Документация](#документация)

---

## Возможности

- 🤖 **Исследовательское тестирование** — AI самостоятельно изучает приложение
- 🔍 **Регрессионное тестирование** — проверка основных функций
- 📊 **User Journey тестирование** — проверка пользовательских сценариев
- 📝 **Генерация отчётов** о найденных проблемах
- ✨ **Генерация автотестов** — создание JUnit 5 + Selenium тестов из сессий
- 📸 **Скриншоты** — автоматические скриншоты каждого действия
- 🧠 **ReAct паттерн** — AI размышляет перед каждым действием
- 🎨 **Monaco Editor** — просмотр и редактирование сгенерированного кода
- 🤖 **LLM улучшение** — пост-обработка тестов через AI

---

## Быстрый старт

### Требования
- Java 21+
- Maven 3.8+
- Chrome/Chromium браузер

### Запуск

```bash
# Сборка проекта
mvn clean package -DskipTests

# Запуск приложения
mvn spring-boot:run -pl ai-testing-app
```

### UI
Откройте браузер: **http://localhost:8080/**

### Использование

1. Введите URL тестируемого сайта
2. Укажите цель тестирования (prompt)
3. Нажмите "Запустить тестирование"
4. Наблюдайте за действиями AI в реальном времени
5. После завершения сгенерируйте автотест

---

## Технологии

| Компонент | Технология |
|-----------|------------|
| Backend | Java 21, Spring Boot 3.5 |
| AI/LLM | Spring AI, OpenAI API |
| Browser Automation | Selenium WebDriver 4.31 |
| Frontend | Vanilla JS, Bootstrap 5, Monaco Editor |
| Build | Maven |
| Database | H2 (in-memory) |

---

## Архитектура

```
┌─────────────────────────────────────────────────────────────────┐
│                      Application.java                           │
│                    (Точка входа Spring Boot)                    │
└─────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┬─────────────┐
        │                     │                     │             │
        ▼                     ▼                     ▼             ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
│  ai-testing-ui   │ │  ai-testing-core │ │ai-testing-testgen│ │  ai-testing-app  │
│  (UI + REST API) │ │   (Ядро системы) │ │ (Генерация тестов)│ │  (Сборка/Запуск) │
└──────────────────┘ └──────────────────┘ └──────────────────┘ └──────────────────┘
```

### Слои (SOLID/GRASP)

```
Domain Layer (interfaces)
    ↓
Application Layer (сервисы)
    ↓
Infrastructure Layer (реализации)
    ↓
Presentation Layer (DTO, Controllers)
```

---

## Действия агента

AI использует 15 типов действий:

### Навигация
- `NAVIGATE_TO` - перейти по URL
- `NAVIGATE_BACK` - назад в браузере
- `NAVIGATE_FORWARD` - вперед в браузере
- `REFRESH` - обновить страницу

### Взаимодействие
- `CLICK` - кликнуть на элемент
- `TYPE` - ввести текст в поле
- `SCROLL_UP` - прокрутить вверх
- `SCROLL_DOWN` - прокрутить вниз

### Исследование
- `EXPLORE_MENU` - исследовать меню
- `EXPLORE_FORMS` - исследовать формы
- `TEST_VALIDATION` - проверить валидацию

### Проверки (Ассерты)
- `ASSERT_PRESENCE` - проверить наличие элемента
- `ASSERT_TEXT` - проверить текст элемента

### Завершение
- `REPORT_ISSUE` - сообщить о проблеме
- `COMPLETE` - завершить тестирование

---

## Генерация автотестов

После завершения сессии можно сгенерировать JUnit 5 тест:

1. Нажмите "Сгенерировать тест"
2. Откроется Monaco Editor с кодом теста
3. При необходимости нажмите "Улучшить с AI"
4. Скачайте готовый тест

### Пример сгенерированного теста

```java
@Test
@Order(1)
@DisplayName("Вход на сайт с правильными данными")
void login_with_valid_credentials() {
    driver.get("https://the-internet.herokuapp.com/login");
    
    WebElement username = driver.findElement(By.cssSelector("#username"));
    username.clear();
    username.sendKeys("tomsmith");
    
    WebElement password = driver.findElement(By.cssSelector("#password"));
    password.clear();
    password.sendKeys("SuperSecretPassword!");
    
    driver.findElement(By.cssSelector("button[type=submit]")).click();
    
    WebElement message = driver.findElement(By.cssSelector(".row"));
    assertTrue(message.getText().contains("successfully logged in"));
}
```

---

## Улучшения (Декабрь 2026)

### ✅ Реализовано

1. **SOLID/GRASP рефакторинг**
   - Разделение DecisionEngineService на 3 сервиса
   - Разделение ObservationService на 4 сервиса
   - Domain/Application/Infrastructure слои

2. **DTO + MapStruct**
   - TestSessionDto, ActionDto, IssueDto
   - Автоматический маппинг Entity → DTO

3. **Скриншоты действий**
   - screenshotBefore / screenshotAfter в base64
   - Отображение в UI в реальном времени

4. **ReAct паттерн**
   - AI размышляет перед каждым действием (поле `thought`)
   - Лучшее планирование и меньше зацикливаний

5. **Monaco Editor**
   - Просмотр и редактирование сгенерированного кода
   - Подсветка синтаксиса Java

6. **LLM улучшение кода**
   - Endpoint `/test/{id}/improve-code`
   - Удаление пустых методов, дубликатов
   - Добавление комментариев и @DisplayName

7. **Улучшенная генерация тестов**
   - Фильтрация значимых действий
   - Удаление дубликатов
   - Группировка в логические сценарии
   - 5-10 методов вместо 90+

8. **Новые действия**
   - ScrollUpAction, AssertPresenceAction, AssertTextAction
   - NavigateBackAction, NavigateForwardAction

### 📊 Статистика улучшений

| Метрика | До | После |
|---------|-----|-------|
| Количество методов в тесте | 90+ | 5-10 |
| Пустых методов | 60+ | 0 |
| Дубликатов | 70+ | 0 |
| Размер теста | ~2500 строк | ~150 строк |
| Действий с скриншотами | 0 | 15/15 |

---

## Документация

Полная документация в папке `docs/`:

| Файл | Описание |
|------|----------|
| [README.md](docs/README.md) | Основная документация |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Архитектура системы |
| [REFACTORING.md](docs/REFACTORING.md) | SOLID/GRASP рефакторинг |
| [UI_FIXES.md](docs/UI_FIXES.md) | 13 исправлений UI |
| [UI_GUIDE.md](docs/UI_GUIDE.md) | Руководство пользователя UI |
| [TEST_GENERATION.md](docs/TEST_GENERATION.md) | Генерация автотестов |
| [SCREENSHOTS_GUIDE.md](docs/SCREENSHOTS_GUIDE.md) | Работа со скриншотами |
| [MIGRATION_PLAN.md](docs/MIGRATION_PLAN.md) | План миграции действий |
| [CLEANUP_PLAN.md](docs/CLEANUP_PLAN.md) | План очистки дубликатов |
| [ARCHITECTURE_IMPROVEMENTS.md](docs/ARCHITECTURE_IMPROVEMENTS.md) | Архитектурные улучшения |
| [TEST_CHECKLIST.md](docs/TEST_CHECKLIST.md) | Чек-лист тестирования |
| [FINAL_SUMMARY.md](docs/FINAL_SUMMARY.md) | Итоговая сводка |

---

## API Endpoints

| Метод | Endpoint | Описание |
|-------|----------|----------|
| POST | `/test/startSession` | Запуск сессии тестирования |
| POST | `/test/stopSession/{id}` | Остановка сессии |
| GET | `/test/sessions` | Список всех сессий |
| GET | `/test/session/{id}` | Детали сессии |
| POST | `/test/{id}/generate-test` | Генерация теста |
| POST | `/test/{id}/improve-code` | Улучшение кода через AI |
| GET | `/test/{id}/test/download` | Скачать тест |

---

## Структура проекта

```
ai-testing/
├── ai-testing-app/          # Точка входа, сборка
├── ai-testing-core/         # Ядро системы
│   ├── domain/              # Domain Layer (интерфейсы)
│   ├── application/         # Application Layer
│   ├── infrastructure/      # Infrastructure Layer (реализации)
│   └── entity/              # JPA Entity
├── ai-testing-ui/           # UI + REST API
│   ├── rest/                # REST Controllers
│   ├── service/             # Application Services
│   ├── mapper/              # MapStruct Mappers
│   └── model/dto/           # DTO
├── ai-testing-testgen/      # Генерация тестов
│   ├── service/             # TestGeneratorService
│   └── template/            # TestTemplateEngine
└── docs/                    # Документация
```

---

## Контакты и поддержка

- **Версия:** 1.0-SNAPSHOT
- **Java:** 21+
- **Последнее обновление:** Декабрь 2026

---

## Лицензия

Проект разработан для внутреннего использования.
