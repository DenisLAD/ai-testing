package ru.sbrf.uddk.ai.testing.domain.model;

import lombok.Builder;
import lombok.Data;

/**
 * Координаты элемента
 */
@Data
@Builder
public class ElementBounds {
    
    private int x;
    private int y;
    private int width;
    private int height;
    private int top;
    private int right;
    private int bottom;
    private int left;
}
