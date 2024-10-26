import java.util.Objects;

/**
 * Helper class to calculate tracker travelled distance
 */
public class Position {
    public final double latitude;
    public final double longitude;
    public final double altitude;
    public final Long time;

    public Position(double latitude, double longitude, double altitude) {
        this(latitude, longitude, altitude, System.currentTimeMillis());
    }

    public Position(double latitude, double longitude, double altitude, Long time) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.time = time;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;                 // Check if the same instance
        if (obj == null || getClass() != obj.getClass()) return false; // Check type compatibility

        Position other = (Position) obj;
        return Double.compare(latitude, other.latitude) == 0 &&
                Double.compare(longitude, other.longitude) == 0 &&
                Double.compare(altitude, other.altitude) == 0; // Compare all fields
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, altitude); // Generate hash based on fields
    }
}