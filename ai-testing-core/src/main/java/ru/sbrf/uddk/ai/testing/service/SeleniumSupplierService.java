package ru.sbrf.uddk.ai.testing.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.sbrf.uddk.ai.testing.config.SeleniumBrowserSettings;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

@Service
public class SeleniumSupplierService implements InitializingBean, DisposableBean, Supplier<WebDriver> {

    private final List<WebDriver> drivers = new CopyOnWriteArrayList<>();
    private final SeleniumBrowserSettings browserSettings;

    @Value("${app.selenium.webdriver-manager-enabled:true}")
    private boolean webDriverManagerEnabled;

    @Value("${app.selenium.chrome-binary:}")
    private String chromeBinary;

    @Value("${app.selenium.chromedriver-path:}")
    private String chromedriverPath;

    public SeleniumSupplierService(SeleniumBrowserSettings browserSettings) {
        this.browserSettings = browserSettings;
    }

    @Override
    public void afterPropertiesSet() {
        if (StringUtils.hasText(chromedriverPath)) {
            System.setProperty("webdriver.chrome.driver", chromedriverPath);
        } else if (webDriverManagerEnabled) {
            WebDriverManager.chromedriver().setup();
        }
    }

    @Override
    public void destroy() {
        if (webDriverManagerEnabled) {
            WebDriverManager.chromedriver().quit();
        }
        drivers.forEach(WebDriver::quit);
    }

    @Override
    public WebDriver get() {
        ChromeOptions options = new ChromeOptions();
        if (StringUtils.hasText(chromeBinary)) {
            options.setBinary(chromeBinary);
        }
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        browserSettings.applyWindowChromeArgs(options);
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-web-security");
        options.addArguments("--allow-running-insecure-content");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-background-timer-throttling");
        options.addArguments("--disable-backgrounding-occluded-windows");
        options.addArguments("--disable-renderer-backgrounding");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation", "disable-infobars"});
        options.setExperimentalOption("useAutomationExtension", false);

        ChromeDriver driver = new ChromeDriver(options);
        browserSettings.applyWindowSize(driver);
        drivers.add(driver);
        return driver;
    }
}
