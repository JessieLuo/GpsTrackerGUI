import nz.sodium.*;
import swidgets.STextField;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Utils {
    // Format time as H:M:S
    public static String formatTime(long time) {
        DateTimeFormatter TIME_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        Instant instant = Instant.ofEpochMilli(time);
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

        return localDateTime.format(TIME_FORMATTER);
    }

    /**
     * Safely convert user input to double value
     * @param textField the {@code STextField} containing user input as text.
     * @param min       the minimum allowed value (inclusive) for the input.
     * @param max       the maximum allowed value (inclusive) for the input.
     * @return an {@code Optional<Double>} containing the parsed and validated input if within range,
     *         or {@code Optional.empty()} if the input is invalid, non-numeric, or out of range.
     */
    public static Cell<Optional<Double>> convertInputs(STextField textField, double min, double max) {
        return textField.text.map(t -> {
            t = t.trim();
            if (t.isEmpty()) {
                return Optional.empty();
            }
            try {
                double val = Double.parseDouble(t);
                if (val >= min && val <= max) {
                    return Optional.of(val);
                } else {
                    return Optional.empty();
                }
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    /**
     * Calculates the 3D distance between two geographic positions, considering both the
     * Earth's surface (horizontal) distance and altitude difference.
     *
     * <p>This method uses the Haversine formula to compute the horizontal distance
     * (great-circle distance) between two points given their <b>latitude</b> and <b>longitude</b>. It then
     * calculates the <b>altitude</b> difference and combines these values with the Pythagorean
     * theorem to obtain the 3D distance in meters.</p>
     *
     * @param pos1 The first position, containing latitude, longitude, and altitude.
     * @param pos2 The second position, containing latitude, longitude, and altitude.
     * @return The 3D distance between the two positions in meters, or 0.0 if either position is null.
     */
    public static double calculateDistance(Position pos1, Position pos2) {
        if (pos1 == null || pos2 == null) return 0.0;

        // Earth's radius in meters
        double horizontalDistance = getHorizontalDistance(pos1, pos2);

        // Calculate vertical distance (altitude difference)
        double deltaAlt = pos2.altitude - pos1.altitude;

        // Use Pythagorean theorem to calculate the 3D distance
        return Math.sqrt(horizontalDistance * horizontalDistance + deltaAlt * deltaAlt);
    }

    /**
     * Calculates the 2D (horizontal) distance between two positions on Earth's surface
     * using the Haversine formula.
     *
     * <p>The Haversine formula calculates the shortest distance over Earth's surface
     * (great-circle distance) using latitude and longitude coordinates:</p>
     *
     * <pre>
     * a = sin^2(\delta(lat) / 2) + cos(lat1) * cos(lat2) * sin^2(\delta(lon) / 2)
     * c = 2 * atan2(\sqrt(a), \sqrt(1 - a))
     * d = R * c
     * </pre>
     * <p>
     * where:
     * - \delta(lat) = lat2 - lat1 and \delta(lon) = lon2 - lon1
     * - R is Earth's radius in meters (6,371,000 meters)
     *
     * @param pos1 The first position, containing latitude and longitude.
     * @param pos2 The second position, containing latitude and longitude.
     * @return The horizontal distance between the two positions in meters.
     */
    private static double getHorizontalDistance(Position pos1, Position pos2) {
        double lat1 = Math.toRadians(pos1.latitude);
        double lat2 = Math.toRadians(pos2.latitude);
        double deltaLat = Math.toRadians(pos2.latitude - pos1.latitude);
        double deltaLon = Math.toRadians(pos2.longitude - pos1.longitude);

        // Haversine formula
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371000.0 * c;
    }
}
