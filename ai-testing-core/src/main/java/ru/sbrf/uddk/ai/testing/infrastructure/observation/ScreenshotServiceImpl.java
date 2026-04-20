package ru.sbrf.uddk.ai.testing.infrastructure.observation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;
import ru.sbrf.uddk.ai.testing.domain.observation.ScreenshotService;
import ru.sbrf.uddk.ai.testing.infrastructure.webdriver.WebDriverProviderAdapter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Сервис скриншотов
 * Реализация по умолчанию
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScreenshotServiceImpl implements ScreenshotService {

    private final WebDriverProviderAdapter webDriverProvider;
    private final String screenshotDir = "./screenshots";
    private final String screenshotFormat = "png";

    @Override
    public String captureScreenshot() {
        try {
            WebDriver driver = webDriverProvider.get();
            
            if (driver instanceof TakesScreenshot takesScreenshot) {
                File tempFile = takesScreenshot.getScreenshotAs(OutputType.FILE);
                byte[] screenshotBytes = Files.readAllBytes(tempFile.toPath());
                
                // Сжимаем если слишком большой
                if (screenshotBytes.length > 500 * 1024) {
                    screenshotBytes = resizeScreenshot(screenshotBytes, 500 * 1024);
                }
                
                return Base64.getEncoder().encodeToString(screenshotBytes);
            }
            
            log.warn("WebDriver does not support screenshots");
            return null;
            
        } catch (IOException e) {
            log.error("Failed to capture screenshot", e);
            return null;
        }
    }
    
    @Override
    public String saveScreenshot(String sessionId) {
        try {
            WebDriver driver = webDriverProvider.get();
            
            if (driver instanceof TakesScreenshot takesScreenshot) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String filename = String.format("screenshot_%s_%s.%s", sessionId, timestamp, screenshotFormat);
                Path path = Paths.get(screenshotDir, filename);
                
                Files.createDirectories(path.getParent());
                
                File tempFile = takesScreenshot.getScreenshotAs(OutputType.FILE);
                Files.copy(tempFile.toPath(), path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                
                log.info("Screenshot saved: {}", path.toAbsolutePath());
                return path.toString();
            }
            
            return null;
            
        } catch (IOException e) {
            log.error("Failed to save screenshot", e);
            return null;
        }
    }
    
    private byte[] resizeScreenshot(byte[] original, int maxSize) {
        // TODO: Реализовать сжатие изображения
        // Для простоты возвращаем оригинал
        return original;
    }
}
