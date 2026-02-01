package ru.sbrf.uddk.ai.testing.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

@Service
public class SeleniumSupplierService implements InitializingBean, DisposableBean, Supplier<WebDriver> {

    private List<WebDriver> drivers = new CopyOnWriteArrayList<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        WebDriverManager.chromedriver().setup();
    }

    @Override
    public void destroy() throws Exception {
        WebDriverManager.chromedriver().quit();
        drivers.forEach(WebDriver::quit);
    }

    @Override
    public WebDriver get() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
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

        options.addArguments("--kiosk");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation", "disable-infobars"});
        options.setExperimentalOption("useAutomationExtension", false);
        ChromeDriver driver = new ChromeDriver(options);

//        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
//        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(3));
//        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(2));

        driver.manage().window().maximize();

        drivers.add(driver);

        return driver;
    }
}
