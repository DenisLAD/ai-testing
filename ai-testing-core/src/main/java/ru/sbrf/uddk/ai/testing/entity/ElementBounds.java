package ru.sbrf.uddk.ai.testing.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ElementBounds {
    private Integer x;
    private Integer y;
    private Integer width;
    private Integer height;
}
