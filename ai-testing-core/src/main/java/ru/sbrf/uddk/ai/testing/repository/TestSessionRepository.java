package ru.sbrf.uddk.ai.testing.repository;

import ru.sbrf.uddk.ai.testing.entity.TestSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Репозиторий для сущности TestSession
 */
@Repository
public interface TestSessionRepository extends JpaRepository<TestSession, UUID> {
}
