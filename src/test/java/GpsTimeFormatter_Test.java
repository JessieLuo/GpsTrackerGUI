import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.*;

public class GpsTimeFormatter_Test {
    @Test
    public void testFormatTime_TypicalTime() {
        // Create a timestamp for 14:30:15 in the system's default timezone
        ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 6, 30, 14, 30, 15, 0, ZoneId.systemDefault());
        long timestamp = zonedDateTime.toInstant().toEpochMilli();

        String expected = "14:30:15"; // Expected formatted time in the system's local time
        assertEquals(expected, Utils.formatTime(timestamp));
    }

    @Test
    public void testFormatTime_Midnight() {
        // Midnight (00:00:00) in the system's default timezone
        ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 6, 30, 0, 0, 0, 0, ZoneId.systemDefault());
        long timestamp = zonedDateTime.toInstant().toEpochMilli();

        String expected = "00:00:00";
        assertEquals(expected, Utils.formatTime(timestamp));
    }

    @Test
    public void testFormatTime_Noon() {
        // Noon (12:00:00) in the system's default timezone
        ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 6, 30, 12, 0, 0, 0, ZoneId.systemDefault());
        long timestamp = zonedDateTime.toInstant().toEpochMilli();

        String expected = "12:00:00";
        assertEquals(expected, Utils.formatTime(timestamp));
    }

    @Test
    public void testFormatTime_BeforeMidnight() {
        // One second before midnight (23:59:59) in the system's default timezone
        ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 6, 30, 23, 59, 59, 0, ZoneId.systemDefault());
        long timestamp = zonedDateTime.toInstant().toEpochMilli();

        String expected = "23:59:59";
        assertEquals(expected, Utils.formatTime(timestamp));
    }

    @Test
    public void testFormatTime_EdgeCaseNearMidnight() {
        // One second before 23:59:59 (23:59:58) in the system's default timezone
        ZonedDateTime zonedDateTime = ZonedDateTime.of(2021, 6, 30, 23, 59, 58, 0, ZoneId.systemDefault());
        long timestamp = zonedDateTime.toInstant().toEpochMilli();

        String expected = "23:59:58";
        assertEquals(expected, Utils.formatTime(timestamp));
    }
}
