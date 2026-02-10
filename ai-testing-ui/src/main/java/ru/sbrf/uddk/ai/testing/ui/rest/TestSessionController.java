package ru.sbrf.uddk.ai.testing.ui.rest;

import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sbrf.uddk.ai.testing.entity.TestSession;
import ru.sbrf.uddk.ai.testing.entity.consts.SessionStatus;
import ru.sbrf.uddk.ai.testing.entity.consts.TestGoal;
import ru.sbrf.uddk.ai.testing.ui.mapper.SessionMapper;
import ru.sbrf.uddk.ai.testing.ui.model.RestTestSession;
import ru.sbrf.uddk.ai.testing.ui.model.StartSessionModel;
import ru.sbrf.uddk.ai.testing.ui.service.TestSessionService;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("test")
public class TestSessionController {

    @Setter(onMethod_ = @Autowired)
    private SessionMapper sessionMapper;

    @Setter(onMethod_ = @Autowired)
    private TestSessionService testSessionService;

    @PostMapping("/startSession")
    public ResponseEntity<String> startSession(@RequestBody StartSessionModel startSession) {
        TestSession testSession = new TestSession();
        testSession.setGoal(TestGoal.EXPLORATORY);
        testSession.setTargetUrl(startSession.getUrl());
        testSession.setId(UUID.randomUUID());
        testSession.setDescription(startSession.getPrompt());
        testSession.setStatus(SessionStatus.RUNNING);

        testSession.setId(UUID.randomUUID());

        testSessionService.startTest(testSession);

        return ResponseEntity.ok(testSession.getId().toString());
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<RestTestSession>> getSessions() {
        return ResponseEntity.ok(testSessionService.getTestSessions().stream().map(sessionMapper::map).toList());
    }

    @GetMapping("/session/{id}")
    public ResponseEntity<RestTestSession> getSession(@PathVariable("id") String id) {
        return ResponseEntity.ok(Objects.requireNonNull(testSessionService.getTestSessions().stream().filter(sess -> sess.getId().toString().equals(id)).map(sessionMapper::map).findFirst().orElse(null)));
    }
}
