package ru.sbrf.uddk.ai.testing.ui.service;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import ru.sbrf.uddk.ai.testing.entity.TestSession;
import ru.sbrf.uddk.ai.testing.entity.consts.SessionStatus;
import ru.sbrf.uddk.ai.testing.interfaces.TestAgentAction;
import ru.sbrf.uddk.ai.testing.model.AgentObservation;
import ru.sbrf.uddk.ai.testing.service.DecisionEngineService;
import ru.sbrf.uddk.ai.testing.service.ObservationService;
import ru.sbrf.uddk.ai.testing.service.SeleniumSupplierService;
import ru.sbrf.uddk.ai.testing.service.actions.CompleteAction;
import ru.sbrf.uddk.ai.testing.service.actions.ReportIssueAction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@Service
@Slf4j
public class TestSessionService {

    @Setter(onMethod_ = @Autowired)
    private SeleniumSupplierService seleniumSupplierService;

    @Setter(onMethod_ = @Autowired)
    private ObservationService observationService;

    @Setter(onMethod_ = @Autowired)
    private DecisionEngineService decisionEngineService;

    @Getter
    private final List<TestSession> testSessions = new ArrayList<>();

    private void test(TestSession testSession) {
        testSessions.add(testSession);
        WebDriver webDriver = seleniumSupplierService.get();
        webDriver.get(testSession.getTargetUrl());
        testSession.setStatus(SessionStatus.RUNNING);

        while (testSession.getStatus() == SessionStatus.RUNNING) {
            try {
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));
                AgentObservation agentObservation = observationService.captureObservation(webDriver, testSession);

                TestAgentAction agentAction = decisionEngineService.decideNextAction(agentObservation);

                String screenshot = agentObservation.getScreenshot();
                AgentAction ac = agentAction.execute(webDriver);

                testSession.addAction(ac);
                ac.setScreenshotBefore(screenshot);

                testSession.addAction(ac);
                if (agentAction instanceof CompleteAction) {
                    testSession.setStatus(SessionStatus.COMPLETED);
                }
                if (agentAction instanceof ReportIssueAction) {
                    testSession.setStatus(SessionStatus.STOPPED);
                }
            } catch (Exception e) {
                log.error("error", e);
                testSession.setStatus(SessionStatus.FAILED);
            }
        }

        webDriver.close();
    }

    public void startTest(TestSession testSession) {
        CompletableFuture.runAsync(() -> test(testSession));
    }
}
