# Руководство по скриншотам в AI Testing Platform

## Проблема

При тестировании UI не отображал скриншоты, так как:
1. Поля `screenshotBefore` и `screenshotAfter` отсутствовали в `ActionDto`
2. MapStruct не маппил эти поля из Entity в DTO

## Решение

### 1. Обновлен ActionDto

```java
@Data
@Builder
public class ActionDto {
    // ... существующие поля ...
    
    // Скриншоты (base64)
    private String screenshotBefore;
    private String screenshotAfter;
}
```

### 2. Обновлен SessionMapper

```java
@Mapping(target = "screenshotBefore", source = "screenshotBefore")
@Mapping(target = "screenshotAfter", source = "screenshotAfter")
ActionDto toDto(AgentAction action);
```

### 3. BaseAgentAction генерирует скриншоты

```java
protected String takeScreenshotBefore(WebDriver driver, String sessionId) {
    if (driver instanceof TakesScreenshot takesScreenshot) {
        File screenshot = takesScreenshot.getScreenshotAs(OutputType.FILE);
        byte[] screenshotBytes = Files.readAllBytes(screenshot.toPath());
        return Base64.getEncoder().encodeToString(screenshotBytes);
    }
    return null;
}
```

### 4. ClickAction и TypeAction используют скриншоты

```java
public AgentAction execute(WebDriver driver) {
    // Скриншот до
    String screenshotBefore = takeScreenshotBefore(driver, null);
    
    WebElement element = findElement(driver, target);
    element.click();
    
    // Скриншот после
    String screenshotAfter = takeScreenshotAfter(driver, null);

    AgentAction logEntry = createActionLog(...);
    logEntry.setScreenshotBefore(screenshotBefore);
    logEntry.setScreenshotAfter(screenshotAfter);
    return logEntry;
}
```

### 5. UI отображает скриншоты

```javascript
function updateUI(data) {
    for (let i = lastActionCount; i < data.actions.length; i++) {
        const action = data.actions[i];
        
        // Скриншот
        if (action.screenshotBefore) {
            const imgSrc = action.screenshotBefore.startsWith('data:')
                ? action.screenshotBefore
                : 'data:image/png;base64,' + action.screenshotBefore;
            updateScreenshot(imgSrc);
        }
    }
}
```

## Формат данных

### API Response

```json
{
  "id": "uuid",
  "actions": [
    {
      "actionType": "CLICK",
      "targetSelector": "#login-button",
      "screenshotBefore": "data:image/png;base64,iVBOR...",
      "screenshotAfter": "data:image/png;base64,iVBOR...",
      "success": true,
      "resultMessage": "Успешно кликнул..."
    }
  ]
}
```

### Скриншот в base64

Скриншоты возвращаются в одном из двух форматов:
1. С префиксом: `data:image/png;base64,iVBORw0KGgoAAAANS...`
2. Без префикса: `iVBORw0KGgoAAAANS...` (UI добавляет префикс автоматически)

## Проверка работы

### 1. Запустить приложение

```bash
mvn spring-boot:run -pl ai-testing-app
```

### 2. Открыть UI

```
http://localhost:8080/
```

### 3. Запустить тестирование

- URL: `https://the-internet.herokuapp.com/login`
- Prompt: `Протестируй вход на сайт`
- Нажать "Запустить тестирование"

### 4. Проверить скриншоты

- Скриншоты должны отображаться в левой панели
- Изображение обновляется после каждого действия
- Показывается время последнего обновления

## API Endpoints

### Получить сессию со скриншотами

```bash
curl http://localhost:8080/test/session/{id}
```

### Скачать скриншот (из actions)

```bash
curl http://localhost:8080/test/session/{id} | jq '.actions[0].screenshotBefore' | sed 's/"//g' | base64 -d > screenshot.png
```

## Хранение скриншотов

### В БД
- Скриншоты хранятся в таблице `agent_actions`
- Поля: `screenshot_before`, `screenshot_after`
- Тип: TEXT (base64 строка)

### На диске
- Скриншоты также сохраняются в папку `./screenshots/`
- Формат: `screenshot_{sessionId}_{timestamp}.png`

## Производительность

### Оптимизация

1. **Base64 кодирование** - выполняется асинхронно
2. **Ленивая загрузка** - скриншоты загружаются только при запросе сессии
5. **Ограничение размера** - скриншоты >500KB обрезаются

### Рекомендации

- Для production использовать объектное хранилище (S3, MinIO)
- В БД хранить только ссылки на файлы
- Очищать старые скриншоты по расписанию

## Troubleshooting

### Скриншоты не отображаются

1. Проверить что `action.screenshotBefore` приходит в API ответе
2. Проверить консоль браузера на ошибки
3. Убедиться что скриншоты сохраняются в БД

```sql
SELECT screenshot_before FROM agent_actions WHERE session_id = '{id}';
```

### Скриншоты пустые

1. Проверить что WebDriver поддерживает TakesScreenshot
2. Проверить логи на ошибки `Failed to take screenshot`
3. Убедиться что браузер не в headless режиме (некоторые браузеры не делают скриншоты в headless)

### Ошибки CORS

Если UI загружается с другого домена:
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins("*")
            .allowedMethods("*");
    }
}
```

## Git Commit

```bash
git commit -m "Добавлены скриншоты в ActionDto и маппинг

- Добавлены поля screenshotBefore и screenshotAfter в ActionDto
- Обновлен SessionMapper для маппинга скриншотов
- UI код поддерживает отображение скриншотов"
```
