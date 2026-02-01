package ru.sbrf.uddk.ai.testing.model;

import ru.sbrf.uddk.ai.testing.entity.AgentAction;
import ru.sbrf.uddk.ai.testing.entity.DiscoveredIssue;
import ru.sbrf.uddk.ai.testing.entity.InteractiveElement;
import lombok.Data;
import org.openqa.selenium.WebDriver;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentObservation {
    private String url;
    private String pageTitle;
    private LocalDateTime timestamp;
    private String screenshot;
    private String domSnapshot;
    private String pageSource;
    private List<InteractiveElement> visibleElements;
    private List<AgentAction> previousActions;
    private List<DiscoveredIssue> discoveredIssues;
    private Double goalProgress;
    private String errorMessage;
    private String goalDescription;
    private WebDriver webDriver;

}