# Архитектура AI Testing Platform

## Обзор

Платформа построена на основе **Maven-мультимодульной архитектуры** с чётким разделением ответственности между модулями.

## Модули

### ai-testing-core

**Назначение**: Ядро системы, содержащее всю бизнес-логику AI-тестирования.

**Основные пакеты**:
- `config` — Spring конфигурация
- `entity` — JPA-сущности для хранения данных
- `entity/consts` — Перечисления (константы системы)
- `interfaces` — Интерфейсы сервисов
- `model` — DTO модели для передачи данных
- `service` — Основные сервисы бизнес-логики
- `service/actions` — Реализации действий агента (15 типов)
- `utils` — Вспомогательные утилиты

**Ключевые классы**:
```
TestAgentService          — Оркестратор тестирования
DecisionEngineService     — AI-движок принятия решений
ObservationService        — Сбор наблюдений (DOM, скриншоты)
SeleniumSupplierService   — Управление WebDriver
ActionRegistryService     — Реестр и фабрика действий
ElementResolverService    — Разрешение веб-элементов
```

### ai-testing-ui

**Назначение**: REST API и пользовательский интерфейс.

**Основные пакеты**:
- `components` — Vaadin компоненты
- `mapper` — MapStruct мапперы для преобразования DTO
- `model` — REST модели (DTO для API)
- `rest` — REST контроллеры
- `service` — Сервисы уровня UI
- `view` — Vaadin представления (views)

**REST контроллеры**:
```
TestSessionController     — /test/startSession, /test/sessions, /test/session/{id}
```

### ai-testing-app

**Назначение**: Точка входа приложения, сборка и запуск.

**Содержит**:
- `Application.java` — главный класс Spring Boot
- `main/frontend/` — Vaadin/React frontend
- `package.json` — frontend зависимости

## Взаимосвязи модулей

```
┌──────────────────────┐
│   ai-testing-app     │ ← Точка входа
│   (assembly/run)     │
└──────────┬───────────┘
           │ depends on
           ▼
┌──────────────────────┐
│   ai-testing-ui      │ ← REST API + UI
│   (presentation)     │
└──────────┬───────────┘
           │ uses
           ▼
┌──────────────────────┐
│   ai-testing-core    │ ← Бизнес-логика
│   (domain logic)     │
└──────────────────────┘
```

## Слои архитектуры

### 1. Presentation Layer (ai-testing-ui)
- REST контроллеры
- Vaadin представления
- DTO маппинг

### 2. Service Layer (ai-testing-core)
- Оркестрация тестирования
- AI принятие решений
- Бизнес правила

### 3. Integration Layer
- Selenium WebDriver
- Spring AI (LLM провайдер)
- Spring Data JPA (БД)

### 4. Data Layer
- H2 Database (in-memory)
- JPA сущности
- Репозитории

## Поток выполнения

```
User Request
     │
     ▼
┌─────────────────────────┐
│  TestSessionController  │ ← REST endpoint
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│  TestSessionService     │ ← UI сервис
└───────────┬─────────────┘
            │ async start
            ▼
┌─────────────────────────┐
│  TestAgentService       │ ← Главный оркестратор
└───────────┬─────────────┘
            │
            ├─────────────────┬─────────────────┐
            │                 │                 │
            ▼                 ▼                 ▼
    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
    │ Observation  │  │ Decision     │  │  Action      │
    │  Service     │  │  Engine      │  │  Registry    │
    └──────────────┘  └──────────────┘  └──────────────┘
            │                 │                 │
            │                 │                 │
            ▼                 ▼                 ▼
    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
    │  Selenium    │  │  Spring AI   │  │  Selenium    │
    │  WebDriver   │  │  (LLM)       │  │  Actions     │
    └──────────────┘  └──────────────┘  └──────────────┘
```

## Диаграмма последовательности

```
User          Controller      Service        Agent         AI Engine      Selenium
 │               │              │              │               │              │
 │─startSession─▶│              │              │               │              │
 │               │─create─────▶│              │               │              │
 │               │  session     │              │               │              │
 │               │              │─async start─▶│               │              │
 │               │              │              │               │              │
 │               │              │              │─capture─────▶│              │
 │               │              │              │  observation  │              │
 │               │              │              │               │              │
 │               │              │              │─decide──────▶│              │
 │               │              │              │  next action  │              │
 │               │              │              │               │              │
 │               │              │              │◀─LLM response─│              │
 │               │              │              │               │              │
 │               │              │              │─execute──────▶│─────────────▶│
 │               │              │              │  action       │  WebDriver   │
 │               │              │              │               │              │
 │               │              │              │─save────────▶│              │
 │               │              │              │  to DB        │              │
 │               │              │              │               │              │
 │◀──────────────│─status─────▶│              │               │              │
 │   session ID   │  response    │              │               │              │
 │               │              │              │               │              │
```

## Конфигурация AI

```yaml
spring:
  ai:
    openai:
      base-url: http://localhost:1234  # Локальный AI сервер
      api-key: ""                       # Пустой для локальных серверов
      chat:
        options:
          model: "local-model"         # Имя модели
          temperature: 0.7
```

## База данных

### Схемы таблиц

**test_sessions**
```sql
CREATE TABLE test_sessions (
    id UUID PRIMARY KEY,
    url VARCHAR(2048) NOT NULL,
    goal VARCHAR(50),
    prompt TEXT,
    status VARCHAR(50),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

**agent_actions**
```sql
CREATE TABLE agent_actions (
    id UUID PRIMARY KEY,
    session_id UUID REFERENCES test_sessions(id),
    action_type VARCHAR(50),
    target_element TEXT,
    result VARCHAR(50),
    message TEXT,
    screenshot_before BYTEA,
    screenshot_after BYTEA,
    dom_snapshot TEXT,
    created_at TIMESTAMP
);
```

**discovered_issues**
```sql
CREATE TABLE discovered_issues (
    id UUID PRIMARY KEY,
    session_id UUID REFERENCES test_sessions(id),
    issue_type VARCHAR(50),
    severity VARCHAR(50),
    description TEXT,
    steps_to_reproduce TEXT,
    created_at TIMESTAMP
);
```

## Кэширование

Система использует кэширование для оптимизации:

- `sessionElementCache` — кэш интерактивных элементов сессии
- DOM кэширование для избежания повторной обработки

## Обработка ошибок

```
AI Error ──────▶ Fallback actions ──────▶ Default exploration
     │
     ▼
Log error ──────▶ Continue cycle
```

## Масштабируемость

### Горизонтальное масштабирование
- Каждая сессия выполняется в отдельном потоке
- Возможно распределение сессий по нодам

### Вертикальное масштабирование
- Настройка пула потоков через `@Async`
- Конфигурация WebDriver пула

## Безопасность

- API ключи для внешних AI провайдеров
- Изоляция сессий тестирования
- Headless браузер в контролируемой среде
