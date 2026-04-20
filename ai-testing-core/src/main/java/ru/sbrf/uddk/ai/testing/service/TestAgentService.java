package ru.sbrf.uddk.ai.testing.service;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import ru.sbrf.uddk.ai.testing.entity.TestSession;
import ru.sbrf.uddk.ai.testing.domain.action.TestAgentAction;
import ru.sbrf.uddk.ai.testing.model.AgentObservation;
import lombok.Setter;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@Service
public class TestAgentService {

    @Setter(onMethod_ = @Autowired)
    private ObservationService observationService;

    @Setter(onMethod_ = @Autowired)
    private SeleniumSupplierService seleniumSupplierService;

    @Setter(onMethod_ = @Autowired)
    private DecisionEngineService decisionEngineService;

    public void run(TestSession testSession) {
        WebDriver webDriver = seleniumSupplierService.get();
        webDriver.get(testSession.getTargetUrl());
        for (int i = 0; i < 100; i++) {
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));
            AgentObservation agentObservation = observationService.captureObservation(webDriver, testSession);
            TestAgentAction agentAction = decisionEngineService.decideNextAction(agentObservation);
            AgentAction ac = agentAction.execute(webDriver);
            testSession.addAction(ac);
        }

        webDriver.close();
    }

}
