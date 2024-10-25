import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Helper class to print tracker element
 */
public class GpsData {
    public final String trackerID;
    public final String latitude;
    public final String longitude;
    public final Long time;

    public GpsData(String trackerID, String latitude, String longitude, Long time) {
        this.trackerID = trackerID;
        this.latitude = latitude;
        this.longitude = longitude;
        this.time = time;
    }

    public String toString() {
        return trackerID + ", Latitude " + latitude + ", Longitude " + longitude + ", Time: " + formatTime(time);
    }

    public boolean equals(GpsData other) {
        return this.trackerID.equals(other.trackerID) &&
                this.latitude.equals(other.latitude) &&
                this.longitude.equals(other.longitude);
    }

    private String formatTime(long time) {
        DateTimeFormatter TIME_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        Instant instant = Instant.ofEpochMilli(time);
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return localDateTime.format(TIME_FORMATTER);
    }
}