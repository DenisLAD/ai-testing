# План удаления дублирующих действий

## Текущее состояние (проблема)

```
service/actions/           ← СТАРЫЕ (15 действий)
  - ClickAction.java
  - TypeAction.java
  - ScrollUpAction.java
  - ... и еще 12

infrastructure/action/     ← НОВЫЕ (7 действий)
  - ClickAction.java
  - TypeAction.java
  - ScrollUpAction.java
  - ScrollDownAction.java
  - NavigateBackAction.java
  - NavigateForwardAction.java
  - AssertPresenceAction.java
  - AssertTextAction.java
```

**Проблема:** Дублирование кода, путаница, лишние файлы.

## Целевое состояние

```
infrastructure/action/     ← ЕДИНСТВЕННЫЕ действия (15)
  - ClickAction.java
  - TypeAction.java
  - ScrollUpAction.java
  - ScrollDownAction.java
  - NavigateBackAction.java
  - NavigateForwardAction.java
  - NavigateToAction.java
  - RefreshAction.java
  - AssertPresenceAction.java
  - AssertTextAction.java
  - ExploreMenuAction.java
  - ExploreFormsAction.java
  - TestValidationAction.java
  - ReportIssueAction.java
  - CompleteAction.java

domain/action/             ← Интерфейсы
  - TestAgentAction.java
  - ActionFactory.java
```

## Этапы очистки

### Этап 1: Перенести старые действия в infrastructure/action

Для каждого файла из `service/actions/`:

1. **ClickAction.java** - уже есть в infrastructure, проверить что актуальная версия
2. **TypeAction.java** - скопировать из service в infrastructure
3. **ScrollUpAction.java** - уже есть в infrastructure
4. **ScrollDownAction.java** - уже есть в infrastructure
5. **NavigateBackAction.java** - уже есть в infrastructure
6. **NavigateForwardAction.java** - уже есть в infrastructure
7. **NavigateToAction.java** - скопировать + добавить @Component
8. **RefreshAction.java** - скопировать + добавить @Component
9. **AssertPresenceAction.java** - уже есть в infrastructure
10. **AssertTextAction.java** - уже есть в infrastructure
11. **ExploreMenuAction.java** - скопировать + добавить @Component
12. **ExploreFormsAction.java** - скопировать + добавить @Component
13. **TestValidationAction.java** - скопировать + добавить @Component
14. **ReportIssueAction.java** - скопировать + добавить @Component
15. **CompleteAction.java** - скопировать + добавить @Component

### Этап 2: Обновить импорты

Найти все импорты:
```bash
grep -r "import.*service\.actions" --include="*.java"
```

Заменить на:
```java
import ru.sbrf.uddk.ai.testing.infrastructure.action.*;
```

### Этап 3: Удалить старые файлы

```bash
# Удалить директорию
rm -rf service/actions/

# Удалить ActionRegistryService
rm service/ActionRegistryService.java
```

### Этап 4: Проверить компиляцию

```bash
mvn clean compile -DskipTests
```

### Этап 5: Проверить логи

При запуске должно быть:
```
INFO  Зарегистрировано действий: 15
DEBUG - ASSERT_PRESENCE
DEBUG - ASSERT_TEXT
DEBUG - CLICK
DEBUG - COMPLETE
DEBUG - EXPLORE_FORMS
DEBUG - EXPLORE_MENU
DEBUG - NAVIGATE_BACK
DEBUG - NAVIGATE_FORWARD
DEBUG - NAVIGATE_TO
DEBUG - REFRESH
DEBUG - REPORT_ISSUE
DEBUG - SCROLL_DOWN
DEBUG - SCROLL_UP
DEBUG - TEST_VALIDATION
DEBUG - TYPE
```

## Преимущества

**Было:**
- 15 действий в service/actions (без DI)
- 7 действий в infrastructure/action (с DI)
- ActionRegistryService (ручная регистрация)
- Путаница какие действия используются

**Стало:**
- 15 действий в infrastructure/action (все с DI)
- ActionFactoryImpl (автоматическая регистрация)
- Четкая архитектура
- Легко добавлять новые

## Риски

1. **Какой-то метод не скопирован** → Действие не работает
   - Решение: Проверить через тестирование

2. **Импорт не обновлен** → Компиляция падает
   - Решение: grep + replace во всем проекте

3. **ActionRegistryService еще используется** → Runtime ошибка
   - Решение: Проверить grep перед удалением

## Чек-лист

- [ ] Скопировать недостающие действия в infrastructure/action
- [ ] Добавить @Component и getType() в каждое
- [ ] Обновить все импорты
- [ ] Проверить компиляцию
- [ ] Удалить service/actions/
- [ ] Удалить ActionRegistryService.java
- [ ] Проверить логи (15 действий)
- [ ] Протестировать UI

## Время выполнения

~1-2 часа на аккуратное выполнение.
