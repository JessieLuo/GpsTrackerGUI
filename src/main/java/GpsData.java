/**
 * Represents data for a GPS tracker, including the tracker ID, latitude, longitude, and timestamp of the event.
 * <p>
 * This class provides a formatted string representation of the data and methods to compare equality
 * of GPS data entries based on tracker ID and location coordinates.
 */
public class GpsData {
    public final String trackerID;
    public final String latitude;
    public final String longitude;
    public final Long time;

    /**
     * Constructs a GpsData instance with the specified tracker ID, latitude, longitude, and timestamp.
     *
     * @param trackerID Unique identifier for the tracker.
     * @param latitude  Latitude of the tracker in degrees.
     * @param longitude Longitude of the tracker in degrees.
     * @param time      Timestamp of the GPS event in milliseconds.
     */
    public GpsData(String trackerID, String latitude, String longitude, Long time) {
        this.trackerID = trackerID;
        this.latitude = latitude;
        this.longitude = longitude;
        this.time = time;
    }

    /**
     * Returns a string representation of the GPS data in the format:
     * "TrackerID, Latitude: <latitude>, Longitude: <longitude>, Time: <formatted_time>"
     *
     * @return Formatted string representing the tracker data.
     */
    public String toString() {
        return trackerID + ", Latitude " + latitude + ", Longitude " + longitude + ", Time: " + formatTime(time);
    }

    /**
     * Compares this GpsData object with another for equality based on tracker ID, latitude, and longitude.
     *
     * @param other The GpsData object to compare.
     * @return True if the tracker ID, latitude, and longitude are equal; otherwise, false.
     */
    public boolean equals(GpsData other) {
        return this.trackerID.equals(other.trackerID) &&
                this.latitude.equals(other.latitude) &&
                this.longitude.equals(other.longitude);
    }

    // Formats the timestamp to a human-readable string using the utility formatTime method
    private String formatTime(long time) {
        return Utils.formatTime(time);
    }
}