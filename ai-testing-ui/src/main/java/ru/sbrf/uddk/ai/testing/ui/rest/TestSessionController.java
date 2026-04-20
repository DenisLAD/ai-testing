package ru.sbrf.uddk.ai.testing.ui.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.sbrf.uddk.ai.testing.entity.TestSession;
import ru.sbrf.uddk.ai.testing.entity.consts.SessionStatus;
import ru.sbrf.uddk.ai.testing.entity.consts.TestGoal;
import ru.sbrf.uddk.ai.testing.ui.mapper.SessionMapper;
import ru.sbrf.uddk.ai.testing.ui.model.StartSessionModel;
import ru.sbrf.uddk.ai.testing.ui.model.dto.TestSessionDto;
import ru.sbrf.uddk.ai.testing.ui.service.TestSessionService;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("test")
@RequiredArgsConstructor
public class TestSessionController {

    private final SessionMapper sessionMapper;
    private final TestSessionService testSessionService;

    @PostMapping("/startSession")
    public ResponseEntity<String> startSession(@RequestBody StartSessionModel startSession) {
        TestSession testSession = new TestSession();
        testSession.setGoal(TestGoal.EXPLORATORY);
        testSession.setTargetUrl(startSession.getUrl());
        testSession.setId(UUID.randomUUID());
        testSession.setDescription(startSession.getPrompt());
        testSession.setStatus(SessionStatus.RUNNING);

        testSessionService.startTest(testSession);

        return ResponseEntity.ok(testSession.getId().toString());
    }

    @PostMapping("/stopSession/{id}")
    public ResponseEntity<String> stopSession(@PathVariable("id") String id) {
        try {
            UUID sessionId = UUID.fromString(id);
            testSessionService.stopTest(sessionId);
            return ResponseEntity.ok("Session stopped");
        } catch (Exception e) {
            log.error("Error stopping session", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<TestSessionDto>> getSessions() {
        List<TestSession> sessions = testSessionService.getTestSessions();
        return ResponseEntity.ok(sessionMapper.toDtoList(sessions));
    }

    @GetMapping("/session/{id}")
    public ResponseEntity<TestSessionDto> getSession(@PathVariable("id") String id) {
        TestSession session = testSessionService.getTestSessions().stream()
            .filter(sess -> sess.getId().toString().equals(id))
            .findFirst()
            .orElse(null);
        
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(sessionMapper.toDto(session));
    }
}
