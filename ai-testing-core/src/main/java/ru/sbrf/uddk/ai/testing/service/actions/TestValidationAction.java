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
            // Находим поле с валидацией (например, required)
//                WebElement requiredField = driver.findElement(By.cssSelector("[required]"));

            // Пытаемся отправить форму с пустым обязательным полем
//                WebElement form = requiredField.findElement(By.xpath("./ancestor::form"));
            WebElement form = driver.findElement(By.xpath("./ancestor::form"));
            WebElement submitButton = form.findElement(By.cssSelector(
                    "button[type='submit'], input[type='submit']"));

            submitButton.click();

            // Проверяем, появилось ли сообщение об ошибке
            Thread.sleep(1000); // Ждем появления ошибки

            boolean hasError = !driver.findElements(By.cssSelector(
                    ".error, .invalid, [aria-invalid='true']")).isEmpty();

            String message = hasError ?
                    "Валидация работает: обнаружена ошибка при пустом обязательном поле" :
                    "Валидация не сработала: ошибка не обнаружена";

            return createActionLog("TEST_VALIDATION", hasError, message);

        } catch (Exception e) {
            log.error("TestValidationAction failed", e);
            return createActionLog("TEST_VALIDATION", false,
                    "Ошибка тестирования валидации: " + e.getMessage());
        }
    }
}
