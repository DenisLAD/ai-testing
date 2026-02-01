package ru.sbrf.uddk.ai.testing.service.actions;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

@Slf4j
public class AssertTextAction extends BaseAgentAction {
    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing AssertTextAction for: {}, expected: {}", target, value);

        try {
            WebElement element = findElement(driver, target);
            String actualText = element.getText();
            boolean matches = actualText.contains(value);

            String message = matches ?
                    String.format("Текст элемента %s содержит '%s'", target, value) :
                    String.format("Текст элемента %s не содержит '%s'. Актуальный текст: '%s'",
                            target, value, actualText);

            return createActionLog("ASSERT_TEXT", matches, message);

        } catch (Exception e) {
            log.error("AssertTextAction failed", e);
            return createActionLog("ASSERT_TEXT", false,
                    String.format("Ошибка проверки текста элемента %s: %s", target, e.getMessage()));
        }
    }
}
