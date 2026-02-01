package ru.sbrf.uddk.ai.testing.service.actions;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

@Slf4j
public class ScrollDownAction extends BaseAgentAction {
    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing ScrollDownAction");

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");

            return createActionLog("SCROLL_DOWN", true, "Успешно прокрутил страницу вниз");

        } catch (Exception e) {
            log.error("ScrollDownAction failed", e);
            return createActionLog("SCROLL_DOWN", false,
                    "Ошибка прокрутки вниз: " + e.getMessage());
        }
    }
}
