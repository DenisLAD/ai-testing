package ru.sbrf.uddk.ai.testing.ui.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import ru.sbrf.uddk.ai.testing.entity.TestSession;
import ru.sbrf.uddk.ai.testing.entity.consts.SessionStatus;
import ru.sbrf.uddk.ai.testing.domain.action.TestAgentAction;
import ru.sbrf.uddk.ai.testing.model.AgentObservation;
import ru.sbrf.uddk.ai.testing.repository.TestSessionRepository;
import ru.sbrf.uddk.ai.testing.service.DecisionEngineService;
import ru.sbrf.uddk.ai.testing.service.ObservationService;
import ru.sbrf.uddk.ai.testing.service.SeleniumSupplierService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@Service
@Slf4j
@RequiredArgsConstructor
public class TestSessionService {

    private final SeleniumSupplierService seleniumSupplierService;
    private final ObservationService observationService;
    private final DecisionEngineService decisionEngineService;
    private final TestSessionRepository testSessionRepository;

    @Getter
    private final List<TestSession> testSessions = new java.util.ArrayList<>();

    @Transactional
    public TestSession startTest(TestSession testSession) {
        // Сохраняем сессию в БД (ID генерируется при сохранении)
        testSessionRepository.save(testSession);
        testSessions.add(testSession);
        CompletableFuture.runAsync(() -> test(testSession));
        return testSession;
    }

    @Transactional
    public void stopTest(UUID sessionId) {
        TestSession session = testSessionRepository.findById(sessionId)
            .orElse(null);
        
        if (session != null && session.getStatus() == SessionStatus.RUNNING) {
            session.setStatus(SessionStatus.STOPPED);
            testSessionRepository.save(session);
            log.info("Session {} stopped", sessionId);
        }
    }

    @Transactional
    private void test(TestSession testSession) {
        WebDriver webDriver = seleniumSupplierService.get();
        webDriver.get(testSession.getTargetUrl());
        testSession.setStatus(SessionStatus.RUNNING);
        testSessionRepository.save(testSession);

        while (testSession.getStatus() == SessionStatus.RUNNING) {
            try {
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));
                AgentObservation agentObservation = observationService.captureObservation(webDriver, testSession);
                TestAgentAction agentAction = decisionEngineService.decideNextAction(agentObservation);
                AgentAction ac = agentAction.execute(webDriver);
                testSession.addAction(ac);

                // Сохраняем действие в БД
                testSessionRepository.save(testSession);

                // Проверяем тип действия через getType()
                String actionType = agentAction.getType();
                if ("COMPLETE".equals(actionType) || "REPORT_ISSUE".equals(actionType)) {
                    testSession.setStatus(SessionStatus.COMPLETED);
                    testSessionRepository.save(testSession);
                }
            } catch (Exception e) {
                log.error("Error during test execution", e);
                testSession.setStatus(SessionStatus.FAILED);
                testSessionRepository.save(testSession);
            }
        }

        webDriver.close();
    }
}
