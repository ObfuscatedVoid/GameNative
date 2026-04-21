package com.winlator.renderer;

import java.util.Locale;

/**
 * How the X server scene is mapped onto the Android surface.
 *
 * FIT     - Uniformly scaled to fit entirely within the surface; aspect ratio preserved; may letterbox.
 * STRETCH - Non-uniformly scaled so the scene fills the entire surface; aspect ratio may be distorted.
 * ZOOM    - Uniformly scaled to fill the surface; aspect ratio preserved; edges may be cropped.
 */
public enum DisplayMode {
    FIT,
    STRETCH,
    ZOOM;

    public static DisplayMode fromString(String value) {
        if (value == null) return FIT;
        try {
            return DisplayMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FIT;
        }
    }

    public String toValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
