package ru.sbrf.uddk.ai.testing.config;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SeleniumBrowserSettings {

    @Value("${app.selenium.window-width:1920}")
    private int windowWidth;

    @Value("${app.selenium.window-height:1080}")
    private int windowHeight;

    public void applyWindowChromeArgs(ChromeOptions options) {
        options.addArguments("--window-size=" + windowWidth + "," + windowHeight);
        options.addArguments("--force-device-scale-factor=1");
    }

    public void applyWindowSize(WebDriver driver) {
        driver.manage().window().setSize(new Dimension(windowWidth, windowHeight));
        log.info("Browser window size set to {}x{}", windowWidth, windowHeight);
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }
}
