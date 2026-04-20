package ru.sbrf.uddk.ai.testing.domain.observation;

/**
 * Сервис скриншотов
 * Отвечает за создание и сохранение скриншотов
 */
public interface ScreenshotService {
    
    /**
     * Делает скриншот и возвращает base64
     */
    String captureScreenshot();
    
    /**
     * Сохраняет скриншот в файл
     */
    String saveScreenshot(String sessionId);
}
