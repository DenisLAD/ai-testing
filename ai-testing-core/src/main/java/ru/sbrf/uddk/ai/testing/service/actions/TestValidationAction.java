package ru.sbrf.uddk.ai.testing.service.actions;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

@Slf4j
public class TestValidationAction extends BaseAgentAction {
    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing TestValidationAction");

        try {
            // Сначала проверяем есть ли формы на странице
            var forms = driver.findElements(By.tagName("form"));
            if (forms.isEmpty()) {
                return createActionLog("TEST_VALIDATION", false,
                        "На странице нет форм для тестирования валидации");
            }

            // Ищем обязательные поля
            var requiredFields = driver.findElements(By.cssSelector("[required]"));
            if (requiredFields.isEmpty()) {
                return createActionLog("TEST_VALIDATION", false,
                        "На странице нет обязательных полей (required) для тестирования");
            }

            // Пытаемся отправить первую форму
            WebElement form = forms.get(0);
            WebElement submitButton;
            
            try {
                submitButton = form.findElement(By.cssSelector(
                        "button[type='submit'], input[type='submit'], button"));
            } catch (Exception e) {
                return createActionLog("TEST_VALIDATION", false,
                        "Не найдена кнопка отправки формы");
            }

            // Очищаем обязательные поля перед отправкой
            for (WebElement field : requiredFields) {
                try {
                    if (field.isDisplayed()) {
                        field.clear();
                    }
                } catch (Exception ignored) {}
            }

            // Отправляем форму
            submitButton.click();

            // Ждем появления ошибки валидации
            Thread.sleep(1000);

            // Проверяем, появилось ли сообщение об ошибке
            boolean hasError = !driver.findElements(By.cssSelector(
                    ".error, .invalid-feedback, [aria-invalid='true'], :invalid")).isEmpty();

            String message = hasError ?
                    "Валидация работает: обнаружена ошибка при пустом поле" :
                    "Валидация не сработала явно, но форма отправлена";

            return createActionLog("TEST_VALIDATION", true, message);

        } catch (Exception e) {
            log.error("TestValidationAction failed", e);
            return createActionLog("TEST_VALIDATION", false,
                    "Ошибка тестирования валидации: " + e.getMessage());
        }
    }
}
