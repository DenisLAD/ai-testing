package ru.sbrf.uddk.ai.testing.ui.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.sbrf.uddk.ai.testing.entity.TestSession;
import ru.sbrf.uddk.ai.testing.repository.TestSessionRepository;
import ru.sbrf.uddk.ai.testing.testgen.model.GeneratedTest;
import ru.sbrf.uddk.ai.testing.testgen.service.TestGeneratorService;
import ru.sbrf.uddk.ai.testing.ui.model.dto.GenerateTestResponseDto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST контроллер для генерации тестов
 */
@Slf4j
@RestController
@RequestMapping("test")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestGenerationController {

    private final TestGeneratorService testGeneratorService;
    private final TestSessionRepository testSessionRepository;

    /**
     * Генерирует JUnit 5 тест по завершённой сессии
     */
    @PostMapping("/{id}/generate-test")
    @Transactional(readOnly = true)
    public ResponseEntity<GenerateTestResponseDto> generateTest(@PathVariable("id") String id) {
        log.info("Генерация теста для сессии: {}", id);

        try {
            TestSession session = findSession(id);
            if (session == null) {
                return ResponseEntity.badRequest()
                    .body(GenerateTestResponseDto.builder()
                        .className("Error")
                        .description("Сессия не найдена: " + id)
                        .build());
            }

            // Инициализируем actions (lazy loading)
            org.hibernate.Hibernate.initialize(session.getActions());

            if (session.getStatus() != ru.sbrf.uddk.ai.testing.entity.consts.SessionStatus.COMPLETED) {
                return ResponseEntity.badRequest()
                    .body(GenerateTestResponseDto.builder()
                        .className("Error")
                        .description("Сессия ещё не завершена. Статус: " + session.getStatus())
                        .build());
            }

            if (session.getActions() == null || session.getActions().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(GenerateTestResponseDto.builder()
                        .className("Error")
                        .description("Сессия не содержит действий")
                        .build());
            }

            GeneratedTest test = testGeneratorService.generateTest(session);

            return ResponseEntity.ok(GenerateTestResponseDto.builder()
                .className(test.getClassName())
                .packageName(test.getPackageName())
                .description(test.getDescription())
                .sourceCode(test.getSourceCode())
                .methodsCount(test.getTestMethods().size())
                .build());

        } catch (Exception e) {
            log.error("Ошибка генерации теста", e);
            return ResponseEntity.internalServerError()
                .body(GenerateTestResponseDto.builder()
                    .className("Error")
                    .description("Ошибка генерации теста: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Скачивает сгенерированный тест в виде файла
     */
    @GetMapping("/{id}/test/download")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadTest(@PathVariable("id") String id) {
        log.info("Скачивание теста для сессии: {}", id);

        try {
            TestSession session = findSession(id);
            if (session == null) {
                return ResponseEntity.notFound().build();
            }

            org.hibernate.Hibernate.initialize(session.getActions());
            GeneratedTest test = testGeneratorService.generateTest(session);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDispositionFormData("attachment", test.getClassName() + ".java");

            return ResponseEntity.ok()
                .headers(headers)
                .body(test.getSourceCode().getBytes());

        } catch (Exception e) {
            log.error("Ошибка скачивания теста", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Сохраняет сгенерированный тест в указанную директорию
     */
    @PostMapping("/{id}/test/save")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, String>> saveTest(
            @PathVariable("id") String id,
            @RequestBody(required = false) Map<String, String> request) {
        log.info("Сохранение теста для сессии: {}", id);

        try {
            TestSession session = findSession(id);
            if (session == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Сессия не найдена"));
            }

            org.hibernate.Hibernate.initialize(session.getActions());
            GeneratedTest test = testGeneratorService.generateTest(session);

            String outputPath = request != null && request.containsKey("path")
                ? request.get("path")
                : "generated-tests";

            Path savedPath = testGeneratorService.saveTestToFile(test, outputPath);

            Map<String, String> response = new HashMap<>();
            response.put("filePath", savedPath.toAbsolutePath().toString());
            response.put("className", test.getClassName());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Ошибка сохранения теста", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    private TestSession findSession(String id) {
        try {
            UUID sessionId = UUID.fromString(id);
            return testSessionRepository.findById(sessionId).orElse(null);
        } catch (Exception e) {
            log.error("Invalid session ID: {}", id, e);
            return null;
        }
    }
}
