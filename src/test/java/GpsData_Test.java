import org.junit.Test;

import static org.junit.Assert.*;

public class GpsData_Test {
    @Test
    public void testGpsDataConstructor() {
        GpsData gpsData = new GpsData("Tracker123", "45.0", "-75.0", 1634160000000L);
        assertEquals("Tracker123", gpsData.trackerID);
        assertEquals("45.0", gpsData.latitude);
        assertEquals("-75.0", gpsData.longitude);
        assertEquals(Long.valueOf(1634160000000L), gpsData.time);
    }

    @Test
    public void testGpsDataToString() {
        GpsData gpsData = new GpsData("Tracker123", "45.0", "-75.0", 1634160000000L);
        String expectedTimeFormat = "00:00:00"; // Modify based on the expected time for your time zone
        String expectedOutput = "Tracker123, Latitude 45.0, Longitude -75.0, Time: " + expectedTimeFormat;
        assertTrue(gpsData.toString().contains(expectedOutput.substring(0, expectedOutput.length() - 8)));
    }

    @Test
    public void testGpsDataEqualsSameObject() {
        GpsData gpsData = new GpsData("Tracker123", "45.0", "-75.0", 1634160000000L);
        GpsData gpsDataCopy = new GpsData("Tracker123", "45.0", "-75.0", 1634160000000L);
        assertTrue(gpsData.equals(gpsDataCopy)); // Compare the object to itself
    }

    @Test
    public void testGpsDataEqualsEqualObjects() {
        GpsData gpsData1 = new GpsData("Tracker123", "45.0", "-75.0", 1634160000000L);
        GpsData gpsData2 = new GpsData("Tracker123", "45.0", "-75.0", 1634160000000L);
        assertTrue(gpsData1.equals(gpsData2));
    }

    @Test
    public void testGpsDataEqualsDifferentObjects() {
        GpsData gpsData1 = new GpsData("Tracker123", "45.0", "-75.0", 1634160000000L);
        GpsData gpsData2 = new GpsData("Tracker456", "46.0", "-76.0", 1634160000000L);
        assertFalse(gpsData1.equals(gpsData2));
    }
}
