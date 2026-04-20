package ru.sbrf.uddk.ai.testing.domain.observation;

import ru.sbrf.uddk.ai.testing.domain.model.Observation;

/**
 * Сервис захвата наблюдений
 * Координирует сбор информации о состоянии страницы
 */
public interface ObservationCaptureService {
    
    /**
     * Захватывает полное наблюдение
     */
    Observation capture();
    
    /**
     * Захватывает скриншот
     */
    String captureScreenshot();
    
    /**
     * Захватывает DOM
     */
    String captureDOM();
}
