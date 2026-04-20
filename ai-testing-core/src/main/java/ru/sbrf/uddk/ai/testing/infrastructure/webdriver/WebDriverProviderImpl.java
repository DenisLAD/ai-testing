package ru.sbrf.uddk.ai.testing.infrastructure.webdriver;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;
import ru.sbrf.uddk.ai.testing.domain.webdriver.WebDriverProvider;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Провайдер WebDriver
 * Реализация для Chrome
 */
@Slf4j
@Component
public class WebDriverProviderImpl implements WebDriverProvider, WebDriverProviderAdapter {

    private final AtomicReference<WebDriver> driverRef = new AtomicReference<>();
    private final ChromeOptions defaultOptions;

    public WebDriverProviderImpl() {
        this.defaultOptions = createDefaultOptions();
    }

    @Override
    public WebDriver get() {
        return driverRef.updateAndGet(driver -> {
            if (driver == null) {
                return createDriver();
            }
            return driver;
        });
    }

    @Override
    public void release(WebDriver driver) {
        if (driver != null) {
            try {
                driver.quit();
                log.info("WebDriver released");
            } catch (Exception e) {
                log.warn("Error releasing WebDriver", e);
            } finally {
                driverRef.set(null);
            }
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            WebDriver driver = get();
            return driver != null;
        } catch (Exception e) {
            log.warn("WebDriver is not available", e);
            return false;
        }
    }

    public void quit() {
        release(driverRef.get());
    }

    private WebDriver createDriver() {
        try {
            log.info("Creating new Chrome WebDriver...");
            WebDriverManager.chromedriver().setup();
            
            ChromeDriver driver = new ChromeDriver(defaultOptions);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            driver.manage().window().maximize();
            
            log.info("Chrome WebDriver created successfully");
            return driver;
            
        } catch (Exception e) {
            log.error("Failed to create Chrome WebDriver", e);
            throw new RuntimeException("Failed to create WebDriver: " + e.getMessage(), e);
        }
    }

    private ChromeOptions createDefaultOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        return options;
    }
}
