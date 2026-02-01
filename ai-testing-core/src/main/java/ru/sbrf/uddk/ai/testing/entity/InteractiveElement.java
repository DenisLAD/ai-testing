package ru.sbrf.uddk.ai.testing.entity;

import ru.sbrf.uddk.ai.testing.entity.consts.InteractionType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "interactive_elements")
@Data
public class InteractiveElement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String sessionId;

    private String tagName;
    private String text;
    private String idAttr;
    private String name;
    private String type;
    private String placeholder;
    private String classes;

    @Column(length = 1000)
    private String selector;

    @Column(length = 2000)
    private String xpath;

    @Embedded
    private ElementBounds bounds;

    private Boolean isVisible;
    private Boolean isEnabled;
    private Boolean isInteractable;

    @Enumerated(EnumType.STRING)
    private InteractionType interactionType;

    @ElementCollection
    @CollectionTable(name = "element_attributes", joinColumns = @JoinColumn(name = "element_id"))
    @MapKeyColumn(name = "attr_name")
    @Column(name = "attr_value", length = 1000)
    private Map<String, String> attributes = new HashMap<>();

    private LocalDateTime discoveredAt;
    private Integer timesInteracted;

    public void incrementInteractionCount() {
        this.timesInteracted = (timesInteracted == null) ? 1 : timesInteracted + 1;
    }
}
