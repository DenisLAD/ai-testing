# План миграции действий на новую архитектуру

## Цель
Перевести все действия из `service/actions` на использование Spring `@Component` и новый интерфейс через `ActionFactory`.

## Текущее состояние
- ✅ `ClickAction` - имеет `@Component` и `getType()`
- ❌ Остальные 14 действий - требуют обновления

## Необходимые изменения для каждого действия

### Шаблон обновления
```java
package ru.sbrf.uddk.ai.testing.service.actions;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class [ActionName] extends BaseAgentAction {
    
    @Override
    public String getType() {
        return "[ACTION_TYPE]";
    }
    
    @Override
    public AgentAction execute(WebDriver driver) {
        // ... существующий код ...
    }
}
```

### Список действий для обновления

| Действие | Тип | Статус |
|----------|-----|--------|
| TypeAction | TYPE | ❌ |
| ScrollUpAction | SCROLL_UP | ❌ |
| ScrollDownAction | SCROLL_DOWN | ❌ |
| AssertPresenceAction | ASSERT_PRESENCE | ❌ |
| AssertTextAction | ASSERT_TEXT | ❌ |
| NavigateBackAction | NAVIGATE_BACK | ❌ |
| NavigateForwardAction | NAVIGATE_FORWARD | ❌ |
| NavigateToAction | NAVIGATE_TO | ❌ |
| RefreshAction | REFRESH | ❌ |
| ExploreMenuAction | EXPLORE_MENU | ❌ |
| ExploreFormsAction | EXPLORE_FORMS | ❌ |
| TestValidationAction | TEST_VALIDATION | ❌ |
| ReportIssueAction | REPORT_ISSUE | ❌ |
| CompleteAction | COMPLETE | ❌ |

## Этапы миграции

### Этап 1: Добавить @Component и getType()
Для каждого действия из списка:
1. Добавить `import org.springframework.stereotype.Component;`
2. Добавить `@Component` после `@Slf4j`
3. Добавить метод `getType()`:
   ```java
   @Override
   public String getType() {
       return "[ACTION_TYPE]";
   }
   ```

### Этап 2: Проверить что все действия делают скриншоты
Убедиться что каждый `execute()` вызывает:
```java
String screenshotBefore = takeScreenshotBefore(driver, null);
// ... действие ...
String screenshotAfter = takeScreenshotAfter(driver, null);

AgentAction logEntry = createActionLog(...);
logEntry.setScreenshotBefore(screenshotBefore);
logEntry.setScreenshotAfter(screenshotAfter);
```

### Этап 3: Удалить ActionRegistryService
После того как все действия станут компонентами:
1. Удалить `ActionRegistryService.java`
2. Удалить импорты из `DecisionEngineService`
3. Использовать только `ActionFactory`

### Этап 4: Обновить промпт AI
Автоматически генерировать список действий из `ActionFactory.getRegisteredTypes()`:
```java
String availableActions = actionFactory.getRegisteredTypes().stream()
    .collect(Collectors.joining(", "));

prompt.append("Доступные действия: ").append(availableActions);
```

## Преимущества новой архитектуры

### Было (ActionRegistryService):
```java
// Жесткая регистрация
actionCreators.put("CLICK", ClickAction::new);
actionCreators.put("TYPE", TypeAction::new);
// ... вручную для каждого

// Создание через new
BaseAgentAction action = creator.get();
action.configure(decision);
```

### Стало (ActionFactoryImpl):
```java
// Автоматическая регистрация через Spring
public ActionFactoryImpl(List<TestAgentAction> actionList) {
    this.actions = actionList.stream()
        .collect(Collectors.toMap(TestAgentAction::getType, ...));
}

// Создание через фабрику
TestAgentAction action = actionFactory.create(decision);
```

### Преимущества:
1. ✅ Автоматическая регистрация новых действий
2. ✅ Легко добавлять новые - просто создать `@Component`
3. ✅ Нет дублирования кода регистрации
4. ✅ Можно получать список всех действий для промпта
5. ✅ Тестирование через мокирование `ActionFactory`

## Проверка после миграции

```bash
# 1. Компиляция
mvn clean compile -DskipTests

# 2. Запуск
mvn spring-boot:run -pl ai-testing-app

# 3. Проверка в логах
# Должно быть: "Зарегистрировано действий: 15"

# 4. Тестирование
# Запустить тестирование и проверить что все действия работают
```

## Таймлайн

| Этап | Время | Статус |
|------|-------|--------|
| 1. @Component + getType() | 30 мин | ⬜ |
| 2. Проверка скриншотов | 20 мин | ⬜ |
| 3. Удаление ActionRegistryService | 10 мин | ⬜ |
| 4. Обновление промпта | 15 мин | ⬜ |
| 5. Тестирование | 30 мин | ⬜ |
| **Итого** | **~2 часа** | |

## Риски

1. **Какой-то Action не имеет `getType()`** → ActionFactory не зарегистрирует
   - Решение: Проверить через unit тест

2. **Два Action с одинаковым `getType()`** → Конфликт в Map
   - Решение: ActionFactory логирует warning

3. **Action требует конструктор с параметрами** → Spring не создаст
   - Решение: Убрать конструкторы, использовать setter

## Мониторинг

После запуска проверить логи:
```
INFO  Зарегистрировано действий: 15
DEBUG - ASSERT_PRESENCE
DEBUG - ASSERT_TEXT
DEBUG - CLICK
...
```

Если меньше 15 - найти какой Action не зарегистрировался.
