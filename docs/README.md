# AI Testing Platform

Платформа для **автономного AI-тестирования веб-приложений**. Система использует искусственный интеллект (LLM) для автоматического исследования веб-интерфейсов, обнаружения проблем и **генерации автотестов на Java**.

## 📑 Оглавление

- [Возможности](#возможности)
- [Архитектура](#архитектура)
- [Технологии](#технологии)
- [Структура проекта](#структура-проекта)
- [Быстрый старт](#быстрый-старт)
- [API](#api)
- [Компоненты системы](#компоненты-системы)
- [Действия агента](#действия-агента)
- [Генерация автотестов](#генерация-автотестов)

---

## Возможности

- 🤖 **Исследовательское тестирование** — AI самостоятельно изучает приложение
- 🔍 **Регрессионное тестирование** — проверка основных функций
- 🛡️ **Проверка безопасности** — поиск XSS, CSRF, инъекций
- ♿ **Тестирование доступности** (accessibility)
- 📊 **User Journey тестирование** — проверка пользовательских сценариев
- 📝 **Генерация отчётов** о найденных проблемах
- ✨ **Генерация автотестов** — создание JUnit 5 + Selenium тестов из сессий

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

---

## Технологии

### Backend (Java 21)

| Технология | Версия | Назначение |
|------------|--------|------------|
| **Spring Boot** | 3.5.10 | Основной фреймворк |
| **Spring AI** | 1.1.2 | Интеграция с AI/LLM |
| **Spring AI OpenAI** | - | Провайдер для OpenAI-совместимых API |
| **Selenium** | 4.39.0 | Автоматизация браузера |
| **WebDriverManager** | 6.3.3 | Управление драйверами браузеров |
| **Spring Data JPA** | - | Работа с базой данных |
| **H2 Database** | - | Встроенная БД (runtime) |
| **Lombok** | - | Генерация кода |
| **MapStruct** | 1.6.3 | Маппинг DTO |
| **Jackson** | - | JSON сериализация |

### Frontend

| Технология | Версия | Назначение |
|------------|--------|------------|
| **Vaadin** | 24.7.7 | UI фреймворк |
| **React** | 18.3.1 | UI библиотека |
| **TypeScript** | 5.7.3 | Типизация |
| **Vite** | 6.3.4 | Сборщик |
| **React Router** | 7.5.2 | Роутинг |

---

## Структура проекта

```
ai-testing/                          # Корневой модуль (pom)
├── pom.xml                          # Родительский POM с dependencyManagement
├── docs/                            # Документация
├── ai-testing-core/                 # Ядро системы тестирования
│   ├── pom.xml
│   └── src/main/java/ru/sbrf/uddk/ai/testing/
│       ├── config/                  # Конфигурация Spring
│       ├── entity/                  # JPA-сущности
│       ├── entity/consts/           # Перечисления
│       ├── interfaces/              # Интерфейсы
│       ├── model/                   # DTO модели
│       ├── service/                 # Бизнес-логика
│       ├── service/actions/         # Реализации действий агента
│       └── utils/                   # Утилиты
├── ai-testing-ui/                   # UI модуль и REST API
│   ├── pom.xml
│   └── src/main/java/ru/sbrf/uddk/ai/testing/ui/
│       ├── components/              # Vaadin компоненты
│       ├── mapper/                  # MapStruct мапперы
│       ├── model/                   # REST модели
│       ├── rest/                    # REST контроллеры
│       ├── service/                 # UI сервисы
│       └── view/                    # Vaadin представления
└── ai-testing-app/                  # Точка входа приложения
    ├── pom.xml
    ├── package.json                 # Frontend зависимости (Vaadin + React)
    └── src/
        ├── main/java/               # Application.java
        └── main/frontend/           # Vaadin/React frontend
```

---

## Быстрый старт

### Предварительные требования

- Java 21+
- Maven 3.8+
- Локальный OpenAI-совместимый сервер (LM Studio, Ollama и т.д.)

### Запуск

1. **Запустите локальный AI-сервер** на `http://localhost:1234`

2. **Настройте подключение** в `application.yml` (если требуется):
   ```yaml
   spring:
     ai:
       openai:
         base-url: http://localhost:1234
         api-key: ""
   ```

3. **Запустите приложение**:
   ```bash
   mvn spring-boot:run
   ```

4. **Создайте тестовую сессию**:
   ```bash
   curl -X POST http://localhost:8080/test/startSession \
     -H "Content-Type: application/json" \
     -d '{
       "url": "https://the-internet.herokuapp.com/login",
       "prompt": "Test login functionality with invalid credentials"
     }'
   ```

5. **Проверьте статус сессии**:
   ```bash
   curl http://localhost:8080/test/sessions
   ```

---

## API

### REST Endpoints

| Метод | Endpoint | Описание |
|-------|----------|----------|
| `POST` | `/test/startSession` | Запуск новой тестовой сессии |
| `GET` | `/test/sessions` | Список всех сессий |
| `GET` | `/test/session/{id}` | Детали сессии по ID |

### Пример запроса на запуск сессии

```json
{
  "url": "https://example.com",
  "goal": "EXPLORATORY",
  "prompt": "Explore the application and find any UI/UX issues"
}
```

### Пример ответа

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "url": "https://example.com",
  "goal": "EXPLORATORY",
  "status": "RUNNING",
  "createdAt": "2026-04-20T10:00:00Z",
  "actions": [],
  "issues": []
}
```

---

## Компоненты системы

### Ядро (ai-testing-core)

| Компонент | Назначение |
|-----------|------------|
| **TestAgentService** | Главный оркестратор: управляет циклом тестирования |
| **DecisionEngineService** | AI-движок: анализирует страницу через LLM, принимает решения |
| **ObservationService** | Собирает наблюдения: скриншоты, DOM, интерактивные элементы |
| **SeleniumSupplierService** | Поставщик WebDriver (Chrome headless) |
| **ActionRegistryService** | Реестр действий: создаёт действия на основе решений AI |
| **ElementResolverService** | Поиск веб-элементов по селекторам |

### Сущности

| Сущность | Таблица | Описание |
|----------|---------|-----------|
| **TestSession** | `test_sessions` | Сессия тестирования: цель, URL, статус |
| **AgentAction** | `agent_actions` | Выполненное действие: тип, результат |
| **DiscoveredIssue** | `discovered_issues` | Обнаруженная проблема: тип, серьёзность |
| **InteractiveElement** | `interactive_elements` | Интерактивный элемент: селектор, атрибуты |

### Перечисления

**TestGoal** (цели тестирования):
- `EXPLORATORY` — Исследовательское
- `REGRESSION` — Регрессионное
- `SECURITY` — Безопасность
- `PERFORMANCE` — Производительность
- `ACCESSIBILITY` — Доступность
- `USER_JOURNEY` — Пользовательский сценарий

**SessionStatus** (статусы сессии):
- `CREATED`, `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`, `STOPPED`, `TIMEOUT`

**IssueSeverity** (серьёзность проблем):
- `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO`

**IssueType** (типы проблем):
- `FUNCTIONAL`, `UI_UX`, `PERFORMANCE`, `SECURITY`, `ACCESSIBILITY`, `COMPATIBILITY`, `CONTENT`, `OTHER`

---

## Действия агента

Система поддерживает **15 типов действий**:

| Действие | Описание |
|----------|----------|
| `CLICK` | Клик по элементу |
| `TYPE` | Ввод текста в поле |
| `NAVIGATE_BACK` | Назад в браузере |
| `NAVIGATE_FORWARD` | Вперёд в браузере |
| `NAVIGATE_TO` | Переход по URL |
| `ASSERT_PRESENCE` | Проверка наличия элемента |
| `ASSERT_TEXT` | Проверка текста элемента |
| `SCROLL_UP` | Прокрутка вверх |
| `SCROLL_DOWN` | Прокрутка вниз |
| `REFRESH` | Обновление страницы |
| `EXPLORE_MENU` | Исследование меню |
| `EXPLORE_FORMS` | Исследование форм |
| `TEST_VALIDATION` | Проверка валидации полей |
| `REPORT_ISSUE` | Сообщение о проблеме |
| `COMPLETE` | Завершение тестирования |

---

## Поток данных (Workflow)

```
1. Пользователь → POST /test/startSession → создаётся TestSession
2. TestSessionService.startTest() → запускает асинхронный цикл
3. Цикл тестирования:
   ├─ ObservationService.captureObservation()
   │  ├─ Делает скриншот страницы
   │  ├─ Извлекает видимый DOM (с оптимизацией)
   │  └─ Сканирует интерактивные элементы
   │
   ├─ DecisionEngineService.decideNextAction()
   │  ├─ Формирует промпт для AI с:
   │  │   • Видимым DOM
   │  │   • Целью тестирования
   │  │   • Историей действий
   │  │   • Обнаруженными проблемами
   │  ├─ Отправляет запрос к LLM (OpenAI-совместимый API)
   │  └─ Парсит ответ AI (JSON с решением)
   │
   ├─ ActionRegistryService.createAction() → создаёт действие
   ├─ AgentAction.execute() → выполняет в Selenium
   └─ TestSession.addAction() → сохраняет в БД
4. Цикл повторяется до COMPLETE или REPORT_ISSUE
```

---

## Ключевые особенности

1. **AI-Driven Decision Making** — все решения о действиях принимает LLM
2. **Оптимизация DOM для AI** — извлекается только видимая часть DOM
3. **Кэширование элементов** — для избежания повторного сканирования
4. **Детальное логирование** — каждое действие сохраняется со скриншотами
5. **Асинхронное выполнение** — тестовые сессии запускаются в отдельных потоках
6. **Fallback логика** — при ошибке AI используются действия по умолчанию
7. **Генерация автотестов** — создание JUnit 5 + Selenium тестов из сессий

---

## Генерация автотестов

После завершения AI-сессии вы можете сгенерировать исполняемый автотест на Java:

### Через REST API

```bash
# Генерация теста
curl -X POST http://localhost:8080/test/{session-id}/generate-test

# Скачивание файла
curl -O http://localhost:8080/test/{session-id}/test/download
```

### Пример сгенерированного теста

```java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LoginInvalidCredentialsTest {
    
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

📖 **Подробная документация**: [TEST_GENERATION.md](TEST_GENERATION.md)

---

## Лицензия

Внутренняя разработка. Все права защищены.
