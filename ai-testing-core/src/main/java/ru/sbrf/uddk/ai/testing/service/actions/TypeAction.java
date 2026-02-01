package ru.sbrf.uddk.ai.testing.service.actions;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

@Slf4j
public class TypeAction extends BaseAgentAction {
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
            log.error("TypeAction failed", e);
            return createActionLog("TYPE", false,
                    String.format("Ошибка ввода в элемент %s: %s", target, e.getMessage()));
        }
    }
}
