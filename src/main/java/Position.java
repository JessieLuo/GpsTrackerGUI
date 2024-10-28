import java.util.Objects;

/**
 * Represents the geographical position of a GPS tracker, including latitude, longitude, altitude, and timestamp.
 * <p>
 * This class provides utility methods for equality comparison and hash generation based on geographical coordinates.
 */
public class Position {
    public final double latitude;  // Latitude in degrees
    public final double longitude; // Longitude in degrees
    public final double altitude;  // Altitude in meters
    public final Long time;        // Timestamp in milliseconds of when the position was recorded

    /**
     * Constructs a Position instance with latitude, longitude, and altitude.
     * The current system time is used as the timestamp.
     *
     * @param latitude  Latitude in degrees.
     * @param longitude Longitude in degrees.
     * @param altitude  Altitude in meters.
     */
    public Position(double latitude, double longitude, double altitude) {
        this(latitude, longitude, altitude, System.currentTimeMillis());
    }

    /**
     * Constructs a Position instance with latitude, longitude, altitude, and a specific timestamp.
     *
     * @param latitude  Latitude in degrees.
     * @param longitude Longitude in degrees.
     * @param altitude  Altitude in meters.
     * @param time      Timestamp in milliseconds.
     */
    public Position(double latitude, double longitude, double altitude, Long time) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.time = time;
    }

    /**
     * Compares this Position object with another for equality based on latitude, longitude, and altitude.
     *
     * @param obj The object to compare with this Position.
     * @return True if the latitude, longitude, and altitude are equal; otherwise, false.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;                 // Check if the same instance
        if (obj == null || getClass() != obj.getClass()) return false; // Check type compatibility

        Position other = (Position) obj;
        return Double.compare(latitude, other.latitude) == 0 &&
                Double.compare(longitude, other.longitude) == 0 &&
                Double.compare(altitude, other.altitude) == 0; // Compare all fields
    }

    /**
     * Generates a hash code for this Position based on its latitude, longitude, and altitude.
     *
     * @return Integer hash code based on position fields.
     */
    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, altitude); // Generate hash based on fields
    }
}