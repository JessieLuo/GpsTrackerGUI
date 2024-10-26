import nz.sodium.Cell;
import nz.sodium.Stream;
import nz.sodium.StreamSink;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class GuiOutline_Test {
    /* Test Display (1) */

    /* Test Ten Simplified Tracker Display */
    @Test
    public void testSimplifiedTracker() {
        // Create test streams
        StreamSink<GpsEvent> gpsEvent1 = new StreamSink<>();
        StreamSink<GpsEvent> gpsEvent2 = new StreamSink<>();
        @SuppressWarnings("unchecked")
        Stream<GpsEvent>[] gpsEvents = new Stream[]{gpsEvent1, gpsEvent2};

        // Call the static method directly
        List<List<Cell<String>>> frpCells = GuiOutline.simplifiedTrackers(gpsEvents);

        // Send test events to simulate data input
        gpsEvent1.send(new GpsEvent("tracker1", 34.05, -118.25, 44.5));
        gpsEvent2.send(new GpsEvent("tracker2", 40.71, -74.01, 32.0));

        // Verify that the cells hold the expected values
        assertEquals("tracker1", frpCells.get(0).get(0).sample()); // trackerId for gpsEvent1
        assertEquals("34.05", frpCells.get(1).get(0).sample());    // latitude for gpsEvent1
        assertEquals("-118.25", frpCells.get(2).get(0).sample());  // longitude for gpsEvent1

        assertEquals("tracker2", frpCells.get(0).get(1).sample()); // trackerId for gpsEvent2
        assertEquals("40.71", frpCells.get(1).get(1).sample());    // latitude for gpsEvent2
        assertEquals("-74.01", frpCells.get(2).get(1).sample());   // longitude for gpsEvent2

        // Ensure no more attributes such as altitude are present
        assertEquals(3, frpCells.size()); // Only three lists: trackerId, latitude, longitude
    }

    @Test
    public void testZeroTrackers() {
        // Test with no trackers
        @SuppressWarnings("unchecked")
        Stream<GpsEvent>[] gpsEvents = new Stream[0];

        List<List<Cell<String>>> frpCells = GuiOutline.simplifiedTrackers(gpsEvents);

        // Since there are no trackers, we expect empty lists for each attribute
        assertEquals(0, frpCells.get(0).size());
        assertEquals(0, frpCells.get(1).size());
        assertEquals(0, frpCells.get(2).size());
    }

    @Test
    public void testSingleTracker() {
        // Test with a single tracker
        StreamSink<GpsEvent> gpsEvent1 = new StreamSink<>();
        @SuppressWarnings("unchecked")
        Stream<GpsEvent>[] gpsEvents = new Stream[]{gpsEvent1};

        List<List<Cell<String>>> frpCells = GuiOutline.simplifiedTrackers(gpsEvents);

        gpsEvent1.send(new GpsEvent("tracker1", 34.05, -118.25, 100)); // Altitude excluded

        // Check that only latitude and longitude are displayed correctly
        assertEquals("tracker1", frpCells.get(0).get(0).sample());
        assertEquals("34.05", frpCells.get(1).get(0).sample());
        assertEquals("-118.25", frpCells.get(2).get(0).sample());
    }

    /* Test current event Display */

    /* Test Display (2) */

    /* Test distance calculate method */
    @Test
    public void testDistanceWithSamePosition() {
        Position pos1 = new Position(45.0, -75.0, 100.0);
        Position pos2 = new Position(45.0, -75.0, 100.0);

        double distance = GuiOutline.calculateDistance(pos1, pos2);

        assertEquals(0.0, distance, 0.001);  // Expecting 0 meters distance for identical positions
    }

    @Test
    public void testDistanceWithOnlyAltitudeDifference() {
        Position pos1 = new Position(45.0, -75.0, 100.0);
        Position pos2 = new Position(45.0, -75.0, 200.0);

        double distance = GuiOutline.calculateDistance(pos1, pos2);

        assertEquals(100.0, distance, 0.001);  // Only altitude difference, so distance should equal altitude difference
    }

    @Test
    public void testDistanceWithHorizontalAndAltitudeDifference() {
        Position pos1 = new Position(45.0, -75.0, 100.0);
        Position pos2 = new Position(45.001, -75.001, 200.0);

        double distance = GuiOutline.calculateDistance(pos1, pos2);

        // Calculated manually or estimated expected value for the given lat/lon/altitude difference
        assertEquals(168.95, distance, 0.1);  // Adjust tolerance based on accuracy required
    }

    @Test
    public void testDistanceWithNullPositions() {
        Position pos2 = new Position(45.0, -75.0, 100.0);

        double distance = GuiOutline.calculateDistance(null, pos2);

        assertEquals(0.0, distance, 0.001);  // Distance should be 0 when any position is null
    }
}