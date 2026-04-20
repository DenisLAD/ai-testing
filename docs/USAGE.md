# Руководство по использованию AI Testing Platform

## Содержание

1. [Настройка окружения](#настройка-окружения)
2. [Запуск приложения](#запуск-приложения)
3. [Создание тестовой сессии](#создание-тестовой-сессии)
4. [Мониторинг выполнения](#мониторинг-выполнения)
5. [Анализ результатов](#анализ-результатов)
6. [Примеры использования](#примеры-использования)

---

## Настройка окружения

### Требования

- **Java**: версия 21 или выше
- **Maven**: версия 3.8 или выше
- **AI Сервер**: локальный OpenAI-совместимый сервер (LM Studio, Ollama, etc.)

### Установка AI сервера

#### Вариант 1: LM Studio

1. Скачайте с https://lmstudio.ai/
2. Установите и запустите
3. Загрузите модель (рекомендуется 7B+ параметров)
4. Запустите локальный сервер на порту 1234

#### Вариант 2: Ollama

```bash
# Установка (Windows)
winget install Ollama.Ollama

# Запуск модели
ollama run llama2

# Сервер автоматически запускается на http://localhost:11434
```

### Конфигурация приложения

Откройте `ai-testing-app/src/main/resources/application.yml`:

```yaml
spring:
  ai:
    openai:
      base-url: http://localhost:1234
      api-key: ""
      chat:
        options:
          model: "local-model"
          temperature: 0.7
```

---

## Запуск приложения

### Через Maven

```bash
# Из корня проекта
mvn spring-boot:run

# Или из модуля ai-testing-app
cd ai-testing-app
mvn spring-boot:run
```

### Через IDE

1. Найдите класс `Application.java` в `ai-testing-app/src/main/java/`
2. Запустите main метод

### Проверка запуска

```bash
# Проверка доступности API
curl http://localhost:8080/actuator/health

# Ожидаемый ответ: {"status":"UP"}
```

---

## Создание тестовой сессии

### Базовый запрос

```bash
curl -X POST http://localhost:8080/test/startSession \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://the-internet.herokuapp.com/login",
    "prompt": "Test login functionality with invalid credentials"
  }'
```

### Расширенный запрос с указанием цели

```bash
curl -X POST http://localhost:8080/test/startSession \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com",
    "goal": "EXPLORATORY",
    "prompt": "Explore the main page and find UI/UX issues"
  }'
```

### Доступные цели тестирования

| Goal | Описание |
|------|----------|
| `EXPLORATORY` | Исследовательское тестирование |
| `REGRESSION` | Регрессионное тестирование |
| `SECURITY` | Проверка безопасности |
| `PERFORMANCE` | Тестирование производительности |
| `ACCESSIBILITY` | Проверка доступности |
| `USER_JOURNEY` | Пользовательский сценарий |

---

## Мониторинг выполнения

### Список всех сессий

```bash
curl http://localhost:8080/test/sessions
```

**Ответ**:
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "url": "https://example.com",
    "goal": "EXPLORATORY",
    "status": "RUNNING",
    "createdAt": "2026-04-20T10:00:00Z"
  }
]
```

### Детали сессии

```bash
curl http://localhost:8080/test/session/550e8400-e29b-41d4-a716-446655440000
```

**Ответ**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "url": "https://example.com",
  "goal": "EXPLORATORY",
  "prompt": "Explore the application",
  "status": "RUNNING",
  "createdAt": "2026-04-20T10:00:00Z",
  "updatedAt": "2026-04-20T10:05:00Z",
  "actions": [
    {
      "id": "action-1",
      "actionType": "NAVIGATE_TO",
      "targetElement": "https://example.com",
      "result": "SUCCESS",
      "message": "Navigated to page"
    },
    {
      "id": "action-2",
      "actionType": "CLICK",
      "targetElement": "button#login",
      "result": "SUCCESS",
      "message": "Clicked on login button"
    }
  ],
  "issues": []
}
```

### Статусы сессии

| Статус | Описание |
|--------|----------|
| `CREATED` | Сессия создана, ожидает запуска |
| `RUNNING` | Тестирование выполняется |
| `PAUSED` | Сессия на паузе |
| `COMPLETED` | Тестирование завершено успешно |
| `FAILED` | Произошла ошибка |
| `STOPPED` | Сессия остановлена пользователем |
| `TIMEOUT` | Превышено время ожидания |

---

## Анализ результатов

### Получение отчёта

```bash
curl http://localhost:8080/test/session/{id}/report
```

### Обнаруженные проблемы

Проблемы сохраняются в таблице `discovered_issues`:

```json
{
  "issues": [
    {
      "id": "issue-1",
      "issueType": "UI_UX",
      "severity": "MEDIUM",
      "description": "Login button is not visible on mobile viewport",
      "stepsToReproduce": "1. Resize browser to mobile width\n2. Observe login button"
    }
  ]
}
```

### Типы проблем

| Тип | Описание |
|-----|----------|
| `FUNCTIONAL` | Функциональные ошибки |
| `UI_UX` | Проблемы интерфейса и UX |
| `PERFORMANCE` | Проблемы производительности |
| `SECURITY` | Уязвимости безопасности |
| `ACCESSIBILITY` | Проблемы доступности |
| `COMPATIBILITY` | Проблемы совместимости |
| `CONTENT` | Проблемы контента |
| `OTHER` | Другое |

### Уровни серьёзности

| Уровень | Описание |
|---------|----------|
| `CRITICAL` | Критическая проблема |
| `HIGH` | Высокая серьёзность |
| `MEDIUM` | Средняя серьёзность |
| `LOW` | Низкая серьёзность |
| `INFO` | Информационное сообщение |

---

## Примеры использования

### 1. Тестирование формы логина

```bash
curl -X POST http://localhost:8080/test/startSession \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://the-internet.herokuapp.com/login",
    "goal": "REGRESSION",
    "prompt": "Test login with invalid credentials. Try username \"invalid\" and password \"wrong123\". Verify error message appears."
  }'
```

### 2. Исследование интернет-магазина

```bash
curl -X POST http://localhost:8080/test/startSession \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://demo.opencart.com/",
    "goal": "EXPLORATORY",
    "prompt": "Explore the e-commerce site. Find products, add to cart, check checkout flow. Look for UI inconsistencies and broken links."
  }'
```

### 3. Проверка доступности

```bash
curl -X POST http://localhost:8080/test/startSession \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com",
    "goal": "ACCESSIBILITY",
    "prompt": "Check accessibility compliance. Verify alt text on images, color contrast, keyboard navigation, ARIA labels."
  }'
```

### 4. User Journey тестирование

```bash
curl -X POST http://localhost:8080/test/startSession \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://demo.testim.io/",
    "goal": "USER_JOURNEY",
    "prompt": "Complete user registration flow: click Sign Up, fill form, submit, verify confirmation. Report any issues."
  }'
```

### 5. Проверка безопасности

```bash
curl -X POST http://localhost:8080/test/startSession \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://demo.testim.io/",
    "goal": "SECURITY",
    "prompt": "Test for common security issues: XSS in forms, CSRF tokens, SQL injection in search fields."
  }'
```

---

## Советы по использованию

### Оптимизация промптов

✅ **Хороший промпт**:
```
"Test the login functionality. Try 3 different invalid credential combinations. 
For each attempt, verify the error message is displayed and is user-friendly.
Report any inconsistencies in error handling."
```

❌ **Плохой промпт**:
```
"Test login"
```

### Рекомендации по моделям AI

- **7B+ параметров** — минимум для адекватной работы
- **13B+ параметров** — рекомендуется для сложных сценариев
- **temperature 0.5-0.8** — баланс между креативностью и точностью

### Отладка

Включите детальное логирование в `application.yml`:

```yaml
logging:
  level:
    ru.sbrf.uddk.ai.testing: DEBUG
    org.openqa.selenium: DEBUG
```

### Ограничения

- **Headless браузер** — некоторые элементы могут отображаться иначе
- **Время сессии** — рекомендуется ограничивать 100 действиями
- **AI точность** — зависит от качества модели и промпта

---

## Устранение проблем

### Ошибка: "Cannot connect to AI server"

**Решение**:
1. Проверьте, что AI сервер запущен
2. Проверьте `base-url` в конфигурации
3. Убедитесь, что порт не занят

### Ошибка: "WebDriver exception"

**Решение**:
1. Установите Chrome/Chromium
2. WebDriverManager автоматически загрузит драйвер
3. Проверьте, что нет блокировок фаервола

### Ошибка: "AI returns invalid JSON"

**Решение**:
1. Используйте более мощную модель
2. Уменьшите temperature (0.3-0.5)
3. Уточните промпт с требованием JSON формата

---

## Поддержка

По вопросам обращайтесь к команде разработки.
