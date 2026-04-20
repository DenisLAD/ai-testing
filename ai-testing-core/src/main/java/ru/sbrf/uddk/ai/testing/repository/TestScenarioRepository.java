package ru.sbrf.uddk.ai.testing.repository;

import ru.sbrf.uddk.ai.testing.entity.TestScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для сущности TestScenario
 */
@Repository
public interface TestScenarioRepository extends JpaRepository<TestScenario, UUID> {

    /**
     * Найти сценарий по хэшу (для проверки дубликатов)
     */
    Optional<TestScenario> findByScenarioHash(String scenarioHash);

    /**
     * Найти сценарии по имени
     */
    List<TestScenario> findByNameContainingIgnoreCase(String name);

    /**
     * Найти сценарии по целевому URL
     */
    List<TestScenario> findByTargetUrl(String targetUrl);

    /**
     * Поиск похожих сценариев (по имени или URL)
     */
    @Query("SELECT ts FROM TestScenario ts WHERE " +
           "LOWER(ts.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "ts.targetUrl LIKE CONCAT('%', :keyword, '%')")
    List<TestScenario> findSimilarScenarios(@Param("keyword") String keyword);

    /**
     * Получить последние выполненные сценарии
     */
    @Query("SELECT ts FROM TestScenario ts ORDER BY ts.lastExecutedAt DESC")
    List<TestScenario> findRecentlyExecuted(int limit);
}
