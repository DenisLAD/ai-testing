package ru.sbrf.uddk.ai.testing.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Embeddable
@Data
public class ActionResult {

    private Boolean success;

    @Column(length = 2000)
    private String message;

    @Column(length = 5000)
    private String errorDetails;

    private String newUrl;
    private Integer statusCode;

    @ElementCollection
    @CollectionTable(name = "result_metrics", joinColumns = @JoinColumn(name = "action_id"))
    @MapKeyColumn(name = "metric_name")
    @Column(name = "metric_value")
    private Map<String, String> metrics = new HashMap<>();
}
