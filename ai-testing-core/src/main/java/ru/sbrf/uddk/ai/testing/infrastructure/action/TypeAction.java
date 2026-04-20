package ru.sbrf.uddk.ai.testing.infrastructure.action;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;

/**
 * Действие: Ввод текста в поле
 */
@Slf4j
@Component
public class TypeAction extends BaseAgentAction {

    @Override
    public String getType() {
        return "TYPE";
    }

    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing TypeAction on: {} with value: {}", target, value);

        try {
            WebElement element = findElement(driver, target);
            element.clear();
            element.sendKeys(value);

            return createActionLog("TYPE", true,
                    String.format("Успешно ввел '%s' в элемент: %s", value, target));

        } catch (Exception e) {
            log.error("TypeAction failed: {}", e.getMessage());
            return createActionLog("TYPE", false,
                    String.format("Ошибка ввода в элемент %s: %s", target, e.getMessage()));
        }
    }
}
