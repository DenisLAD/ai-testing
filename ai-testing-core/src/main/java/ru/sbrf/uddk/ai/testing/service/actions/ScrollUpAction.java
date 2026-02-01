package ru.sbrf.uddk.ai.testing.service.actions;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

@Slf4j
public class ScrollUpAction extends BaseAgentAction {
    @Override
    public AgentAction execute(WebDriver driver) {
        log.info("Executing ScrollUpAction");

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("window.scrollTo(0, 0);");

            return createActionLog("SCROLL_UP", true, "Успешно прокрутил страницу вверх");

        } catch (Exception e) {
            log.error("ScrollUpAction failed", e);
            return createActionLog("SCROLL_UP", false,
                    "Ошибка прокрутки вверх: " + e.getMessage());
        }
    }
}
