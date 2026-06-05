package ru.sbrf.uddk.ai.testing.ui.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sbrf.uddk.ai.testing.domain.action.TestAgentAction;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import ru.sbrf.uddk.ai.testing.entity.TestSession;
import ru.sbrf.uddk.ai.testing.entity.consts.SessionStatus;
import ru.sbrf.uddk.ai.testing.model.AgentObservation;
import ru.sbrf.uddk.ai.testing.repository.TestSessionRepository;
import ru.sbrf.uddk.ai.testing.service.DecisionEngineService;
import ru.sbrf.uddk.ai.testing.service.ObservationService;
import ru.sbrf.uddk.ai.testing.service.SeleniumSupplierService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

@Service
@Slf4j
@RequiredArgsConstructor
public class TestSessionService {

    private final SeleniumSupplierService seleniumSupplierService;
    private final ObservationService observationService;
    private final DecisionEngineService decisionEngineService;
    private final TestSessionRepository testSessionRepository;

    private final ConcurrentHashMap<UUID, AtomicBoolean> stopRequests = new ConcurrentHashMap<>();

    @Getter
    private final List<TestSession> testSessions = new java.util.ArrayList<>();

    @Transactional
    public TestSession startTest(TestSession testSession) {
        testSessionRepository.save(testSession);
        testSessions.add(testSession);
        stopRequests.put(testSession.getId(), new AtomicBoolean(false));
        CompletableFuture.runAsync(() -> test(testSession));
        return testSession;
    }

    @Transactional
    public void stopTest(UUID sessionId) {
        requestStop(sessionId);

        testSessionRepository.findById(sessionId).ifPresent(session -> {
            if (session.getStatus() == SessionStatus.RUNNING) {
                session.setStatus(SessionStatus.STOPPED);
                session.setFinishedAt(LocalDateTime.now());
                testSessionRepository.save(session);
                syncInMemorySession(session);
                log.info("Session {} stop requested", sessionId);
            }
        });
    }

    private void test(TestSession testSession) {
        UUID sessionId = testSession.getId();
        WebDriver webDriver = seleniumSupplierService.get();
        try {
            webDriver.get(testSession.getTargetUrl());
            markRunning(sessionId, testSession);

            while (shouldContinue(sessionId)) {
                try {
                    waitBetweenSteps(sessionId, 2);
                    if (!shouldContinue(sessionId)) {
                        break;
                    }

                    AgentObservation agentObservation = observationService.captureObservation(webDriver, testSession);
                    if (!shouldContinue(sessionId)) {
                        break;
                    }

                    TestAgentAction agentAction = decisionEngineService.decideNextAction(agentObservation);
                    if (!shouldContinue(sessionId)) {
                        log.info("Session {} stopped before action execution", sessionId);
                        break;
                    }

                    AgentAction actionResult = agentAction.execute(webDriver);
                    if (!shouldContinue(sessionId)) {
                        log.info("Session {} stopped after action execution", sessionId);
                        break;
                    }

                    testSession.addAction(actionResult);
                    String actionType = agentAction.getType();
                    if ("COMPLETE".equals(actionType) || "REPORT_ISSUE".equals(actionType)) {
                        testSession.markAsCompleted();
                    }

                    persistSessionIfRunning(testSession);
                    if (testSession.getStatus() == SessionStatus.COMPLETED) {
                        break;
                    }
                } catch (Exception e) {
                    if (isStopRequested(sessionId)) {
                        log.info("Session {} interrupted by stop request", sessionId);
                        break;
                    }
                    log.error("Error during test execution for session {}", sessionId, e);
                    markFailed(sessionId, testSession);
                    break;
                }
            }
        } finally {
            stopRequests.remove(sessionId);
            webDriver.quit();
        }
    }

    private void markRunning(UUID sessionId, TestSession testSession) {
        testSession.setStatus(SessionStatus.RUNNING);
        testSessionRepository.save(testSession);
    }

    private void markFailed(UUID sessionId, TestSession testSession) {
        testSession.setStatus(SessionStatus.FAILED);
        testSession.setFinishedAt(LocalDateTime.now());
        testSessionRepository.save(testSession);
        syncInMemorySession(testSession);
    }

    private void persistSessionIfRunning(TestSession testSession) {
        UUID sessionId = testSession.getId();
        if (!shouldContinue(sessionId)) {
            syncStatusFromDatabase(testSession);
            return;
        }
        testSessionRepository.save(testSession);
    }

    private void syncStatusFromDatabase(TestSession testSession) {
        testSessionRepository.findById(testSession.getId()).ifPresent(dbSession -> {
            testSession.setStatus(dbSession.getStatus());
            if (dbSession.getFinishedAt() != null) {
                testSession.setFinishedAt(dbSession.getFinishedAt());
            }
        });
    }

    private boolean shouldContinue(UUID sessionId) {
        return !isStopRequested(sessionId)
                && testSessionRepository.findById(sessionId)
                .map(session -> session.getStatus() == SessionStatus.RUNNING)
                .orElse(false);
    }

    private void requestStop(UUID sessionId) {
        stopRequests.computeIfAbsent(sessionId, ignored -> new AtomicBoolean(false)).set(true);
        testSessions.stream()
                .filter(session -> sessionId.equals(session.getId()))
                .findFirst()
                .ifPresent(session -> session.setStatus(SessionStatus.STOPPED));
    }

    private boolean isStopRequested(UUID sessionId) {
        AtomicBoolean stopFlag = stopRequests.get(sessionId);
        return stopFlag != null && stopFlag.get();
    }

    private void waitBetweenSteps(UUID sessionId, long seconds) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadlineNanos && shouldContinue(sessionId)) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
        }
    }

    private void syncInMemorySession(TestSession updatedSession) {
        testSessions.stream()
                .filter(session -> updatedSession.getId().equals(session.getId()))
                .findFirst()
                .ifPresent(session -> {
                    session.setStatus(updatedSession.getStatus());
                    if (updatedSession.getFinishedAt() != null) {
                        session.setFinishedAt(updatedSession.getFinishedAt());
                    }
                });
    }
}
