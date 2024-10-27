import nz.sodium.Cell;
import nz.sodium.Stream;
import nz.sodium.StreamSink;
import nz.sodium.Unit;
import org.junit.Test;
import swidgets.SButton;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class GpsGUI_Test {
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
        List<List<Cell<String>>> frpCells = GpsGUI.simplifiedTrackers(gpsEvents);

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

        List<List<Cell<String>>> frpCells = GpsGUI.simplifiedTrackers(gpsEvents);

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

        List<List<Cell<String>>> frpCells = GpsGUI.simplifiedTrackers(gpsEvents);

        gpsEvent1.send(new GpsEvent("tracker1", 34.05, -118.25, 100)); // Altitude excluded

        // Check that only latitude and longitude are displayed correctly
        assertEquals("tracker1", frpCells.get(0).get(0).sample());
        assertEquals("34.05", frpCells.get(1).get(0).sample());
        assertEquals("-118.25", frpCells.get(2).get(0).sample());
    }

    /* Test current event Display */
    @Test
    public void testContentClearAfter3Sec() throws InterruptedException {
        // Set up the GPS event streams
        StreamSink<GpsEvent> gpsEvent1 = new StreamSink<>();
        @SuppressWarnings("unchecked")
        Stream<GpsEvent>[] gpsEvents = new Stream[]{gpsEvent1};

        // get the current event content
        Cell<String> content = GpsGUI.currentTracker(gpsEvents);

        // Simulate sending an event
        GpsEvent event = new GpsEvent("Tracker1", 34.05, -118.25, 100);
        gpsEvent1.send(event);

        // the content initially should contain the event data
        assertFalse(content.sample().isEmpty());

        // Wait for over 3 seconds to simulate the timeout
        TimeUnit.SECONDS.sleep(4);

        // the content should be cleared to an empty string after 3 seconds
        assertEquals("", content.sample());
    }

    @Test
    public void testContentNoClearIfNewEventBefore3Sec() throws InterruptedException {
        StreamSink<GpsEvent> gpsEvent1 = new StreamSink<>();
        @SuppressWarnings("unchecked")
        Stream<GpsEvent>[] gpsEvents = new Stream[]{gpsEvent1};

        Cell<String> content = GpsGUI.currentTracker(gpsEvents);

        GpsEvent firstEvent = new GpsEvent("Tracker1", 34.05, -118.25, 100);
        gpsEvent1.send(firstEvent);
        assertFalse(content.sample().isEmpty());

        // Wait for 2 seconds, then send a second event before the 3-second timeout
        TimeUnit.SECONDS.sleep(2);

        GpsEvent secondEvent = new GpsEvent("Tracker2", 40.71, -74.01, 200);
        gpsEvent1.send(secondEvent);
        // Content should still not be empty after the second event
        assertFalse(content.sample().isEmpty());

        // Wait an additional 2 seconds, verifying the second event reset the timeout
        TimeUnit.SECONDS.sleep(2);

        // Content should not be cleared due to not over 3 seconds
        assertFalse(content.sample().isEmpty());
    }

    /* Test Display (2) */

    /* Test distance calculate method */
    @Test
    public void testDistanceWithSamePosition() {
        Position pos1 = new Position(45.0, -75.0, 100.0);
        Position pos2 = new Position(45.0, -75.0, 100.0);

        double distance = GpsGUI.calculateDistance(pos1, pos2);

        assertEquals(0.0, distance, 0.001);  // Expecting 0 meters distance for identical positions
    }

    @Test
    public void testDistanceWithOnlyAltitudeDifference() {
        Position pos1 = new Position(45.0, -75.0, 100.0);
        Position pos2 = new Position(45.0, -75.0, 200.0);

        double distance = GpsGUI.calculateDistance(pos1, pos2);

        assertEquals(100.0, distance, 0.001);  // Only altitude difference, so distance should equal altitude difference
    }

    @Test
    public void testDistanceWithHorizontalAndAltitudeDifference() {
        Position pos1 = new Position(45.0, -75.0, 100.0);
        Position pos2 = new Position(45.001, -75.001, 200.0);

        double distance = GpsGUI.calculateDistance(pos1, pos2);

        // Calculated manually or estimated expected value for the given lat/lon/altitude difference
        assertEquals(168.95, distance, 0.1);  // Adjust tolerance based on accuracy required
    }

    @Test
    public void testDistanceWithNullPositions() {
        Position pos2 = new Position(45.0, -75.0, 100.0);

        double distance = GpsGUI.calculateDistance(null, pos2);

        assertEquals(0.0, distance, 0.001);  // Distance should be 0 when any position is null
    }

    /* Test filter events validation */
    @Test
    public void testEventLatitudeRange() throws NoSuchFieldException, IllegalAccessException {
        // Set up range values for latitude filtering
        List<Cell<Optional<Double>>> rangeVals = createRangeVals(50.0, 40.0, -70.0, -80.0);

        // Set up SButton and StreamSink
        SButton setButton = new SButton("Set");
        StreamSink<GpsEvent> gpsStream = new StreamSink<>();

        // Call filteredEvents with the setButton and gpsStream
        List<Cell<String>> filteredCells = GpsGUI.filteredEvents(rangeVals, setButton, 5000L, gpsStream);

        // Simulate button click to apply the ranges
        simulateButtonClick(setButton);

        // Send GPS events and assert results
        gpsStream.send(new GpsEvent("Tracker1", 45.0, -75.0, 100.0)); // Latitude in range
        assertFalse(filteredCells.get(1).sample().isEmpty());

        gpsStream.send(new GpsEvent("Tracker2", 55.0, -75.0, 100.0)); // Latitude out of range
        assertTrue(filteredCells.get(1).sample().isEmpty());
    }

    @Test
    public void testEventLongitudeRange() throws NoSuchFieldException, IllegalAccessException {
        List<Cell<Optional<Double>>> rangeVals = createRangeVals(50.0, 40.0, -70.0, -80.0);

        SButton setButton = new SButton("Set");
        StreamSink<GpsEvent> gpsStream = new StreamSink<>();
        List<Cell<String>> filteredCells = GpsGUI.filteredEvents(rangeVals, setButton, 5000L, gpsStream);

        simulateButtonClick(setButton);

        gpsStream.send(new GpsEvent("Tracker1", 45.0, -75.0, 100.0)); // Longitude in range
        assertFalse(filteredCells.get(1).sample().isEmpty());

        gpsStream.send(new GpsEvent("Tracker2", 45.0, 75.0, 100.0)); // Longitude out of range
        assertTrue(filteredCells.get(1).sample().isEmpty());
    }

    @Test
    public void testEventLatAndLonRange() throws NoSuchFieldException, IllegalAccessException {
        List<Cell<Optional<Double>>> rangeVals = createRangeVals(50.0, 40.0, -70.0, -80.0);

        SButton setButton = new SButton("Set");
        StreamSink<GpsEvent> gpsStream = new StreamSink<>();
        List<Cell<String>> filteredCells = GpsGUI.filteredEvents(rangeVals, setButton, 5000L, gpsStream);

        simulateButtonClick(setButton);

        gpsStream.send(new GpsEvent("Tracker1", 45.0, -75.0, 100.0)); // Both lat and lon in range
        assertFalse(filteredCells.get(1).sample().isEmpty());

        gpsStream.send(new GpsEvent("Tracker2", 5.0, -85.0, 100.0)); // Both lat and lon out of range
        assertTrue(filteredCells.get(1).sample().isEmpty());
    }

    /* Test filter events distance */
    @Test
    public void testIfDistanceBySameTracker() {
    }

    @Test
    public void testIfDistanceTakeOnlyEachInterval() {
    }


    // Helper method to create range values for latitude and longitude
    private List<Cell<Optional<Double>>> createRangeVals(double latMax, double latMin, double lonMax, double lonMin) {
        return Arrays.asList(
                new Cell<>(Optional.of(latMax)),
                new Cell<>(Optional.of(latMin)),
                new Cell<>(Optional.of(lonMax)),
                new Cell<>(Optional.of(lonMin))
        );
    }

    // Helper method to simulate a button click using reflection
    private void simulateButtonClick(SButton button) throws NoSuchFieldException, IllegalAccessException {
        Field sClickedField = SButton.class.getDeclaredField("sClicked");
        sClickedField.setAccessible(true);
        @SuppressWarnings("unchecked")
        StreamSink<Unit> sClicked = (StreamSink<Unit>) sClickedField.get(button);
        sClicked.send(Unit.UNIT);
    }
}