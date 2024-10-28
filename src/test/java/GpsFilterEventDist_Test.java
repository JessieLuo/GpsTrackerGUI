import nz.sodium.Cell;
import nz.sodium.StreamSink;
import nz.sodium.Unit;
import org.junit.Test;
import swidgets.SButton;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class GpsFilterEventDist_Test {
    @Test
    public void testIfDistanceCumulativeCorrect() throws NoSuchFieldException, IllegalAccessException, InterruptedException {
        List<Cell<Optional<Double>>> rangeVals = createRangeVals(50, -50, 100, -100);

        SButton setButton = new SButton("Set");
        StreamSink<GpsEvent> gpsStream = new StreamSink<>();
        // Simulate filter events
        EventProcessor.filteredEvents(rangeVals, setButton, 5000L, gpsStream);

        simulateButtonClick(setButton);

        // Send events with different tracker IDs; assume altitude has been converted
        GpsEvent event1 = new GpsEvent("Test1Tracker1", 7.8, 98.37, 100.0);
        GpsEvent event2 = new GpsEvent("Test1Tracker1", 8.0, 98.40, 105.0);
        GpsEvent event3 = new GpsEvent("Test1Tracker1", 9.0, 98.50, 110.0);
        GpsEvent event4 = new GpsEvent("Test1Tracker1", 10.0, 98.6, 110.0);

        // Define the actual travelled distance value for each tracker
        double tracker1Dist1 = Utils.calculateDistance(
                new Position(event1.latitude, event1.longitude, event1.altitude),
                new Position(event2.latitude, event2.longitude, event2.altitude));
        double tracker1Dist2 = Utils.calculateDistance(
                new Position(event2.latitude, event2.longitude, event2.altitude),
                new Position(event3.latitude, event3.longitude, event3.altitude));
        double tracker2Dist3 = Utils.calculateDistance(
                new Position(event3.latitude, event3.longitude, event3.altitude),
                new Position(event4.latitude, event4.longitude, event4.altitude));
        double tracker1ExpectDist = tracker1Dist1 + tracker1Dist2 + tracker2Dist3;

        // Simulate event sending
        gpsStream.send(event1);
        Thread.sleep(100);
        gpsStream.send(event2);
        Thread.sleep(100);
        gpsStream.send(event3);
        Thread.sleep(100);
        gpsStream.send(event4);
        Thread.sleep(100);

        // Ensure when tracker arrive position 4 from 1, all the distance has been cumulative
        assertEquals(tracker1ExpectDist, EventProcessor.getTotalDistancesRecord().get("Test1Tracker1"), 0.1);
    }

    @Test
    public void testIfDistanceBySameTracker() throws NoSuchFieldException, IllegalAccessException {
        List<Cell<Optional<Double>>> rangeVals = createRangeVals(50.0, -50.0, 100.0, -100.0);

        SButton setButton = new SButton("Set");
        StreamSink<GpsEvent> gpsStream = new StreamSink<>();
        // Simulate filter events
        EventProcessor.filteredEvents(rangeVals, setButton, 5000L, gpsStream);

        simulateButtonClick(setButton);

        // Send events with different tracker IDs; assume altitude has been converted
        GpsEvent event1 = new GpsEvent("Test2Tracker1", 7.8, 98.37, 100.0);
        GpsEvent event2 = new GpsEvent("Test2Tracker2", 8.0, 98.40, 105.0);
        GpsEvent event3 = new GpsEvent("Test2Tracker3", 9.0, 98.50, 110.0);
        GpsEvent event4 = new GpsEvent("Test2Tracker2", 8.2, 98.42, 107.0);
        GpsEvent event5 = new GpsEvent("Test2Tracker1", 8.5, 98.45, 102.0);

        // Define the actual travelled distance value for each tracker
        double tracker1ExpectDist = Utils.calculateDistance(
                new Position(event1.latitude, event1.longitude, event1.altitude),
                new Position(event5.latitude, event5.longitude, event5.altitude));
        double tracker2ExpectDist = Utils.calculateDistance(
                new Position(event2.latitude, event2.longitude, event2.altitude),
                new Position(event4.latitude, event4.longitude, event4.altitude));
        double tracker3ExpectDist = 0.0; // tracker3 no next position

        // Simulate event sending
        gpsStream.send(event1);
        gpsStream.send(event2);
        gpsStream.send(event3);
        gpsStream.send(event4);
        gpsStream.send(event5);

        // Ensure all the cumulative distance value correspond to its tracker exactly
        assertEquals(tracker1ExpectDist, EventProcessor.getTotalDistancesRecord().get("Test2Tracker1"), 0.0001);
        assertEquals(tracker2ExpectDist, EventProcessor.getTotalDistancesRecord().get("Test2Tracker2"), 0.0001);
        assertEquals(tracker3ExpectDist, EventProcessor.getTotalDistancesRecord().get("Test2Tracker3"), 0.0001);

        // Ensure that positions from Tracker 2 are not used in distance calculations with Tracker 1
        double mistakeTracker1Dist = Utils.calculateDistance(
                new Position(event1.latitude, event1.longitude, event1.altitude),
                new Position(event2.latitude, event2.longitude, event2.altitude));

        assertNotEquals(mistakeTracker1Dist, EventProcessor.getTotalDistancesRecord().get("Test2Tracker1"), 0.0001);
    }

    /* Test filter events distance */
    @Test
    public void testIfDistanceOnlyCalcFilteredEvents() throws NoSuchFieldException, IllegalAccessException {
        List<Cell<Optional<Double>>> rangeVals = createRangeVals(9.0, 8.0, 98.46, 98.30);

        SButton setButton = new SButton("Set");
        StreamSink<GpsEvent> gpsStream = new StreamSink<>();
        // Simulate filter events
        EventProcessor.filteredEvents(rangeVals, setButton, 5000L, gpsStream);

        simulateButtonClick(setButton);

        // Send events with different tracker IDs; assume altitude has been converted
        GpsEvent event1 = new GpsEvent("Test3Tracker1", 7.8, 98.37, 100.0); // lat invalid
        GpsEvent event2 = new GpsEvent("Test3Tracker1", 8.0, 98.40, 105.0);
        GpsEvent event3 = new GpsEvent("Test3Tracker1", 9.0, 98.50, 110.0); // lon invalid
        GpsEvent event4 = new GpsEvent("Test3Tracker1", 8.2, 98.42, 107.0);
        GpsEvent event5 = new GpsEvent("Test3Tracker1", 8.5, 98.45, 102.0);

        // Define the actual travelled distance value for each tracker
        double tracker1Dist1 = Utils.calculateDistance(
                new Position(event2.latitude, event2.longitude, event2.altitude),
                new Position(event4.latitude, event4.longitude, event4.altitude));
        double tracker1Dist2 = Utils.calculateDistance(
                new Position(event4.latitude, event4.longitude, event4.altitude),
                new Position(event5.latitude, event5.longitude, event5.altitude));
        double tracker1ExpectDist = tracker1Dist1 + tracker1Dist2;

        // Simulate event sending
        gpsStream.send(event1);
        gpsStream.send(event2);
        gpsStream.send(event3);
        gpsStream.send(event4);
        gpsStream.send(event5);

        // Ensure all the cumulative distance value correspond to its tracker exactly
        assertEquals(tracker1ExpectDist, EventProcessor.getTotalDistancesRecord().get("Test3Tracker1"), 0.1);

        // For example, add an event as correct position but actually the event is not
        double mistakeTracker1Dist = Utils.calculateDistance(
                new Position(event1.latitude, event1.longitude, event1.altitude),
                new Position(event2.latitude, event2.longitude, event2.altitude))
                + tracker1Dist1 + tracker1Dist2;
        assertNotEquals(mistakeTracker1Dist, EventProcessor.getTotalDistancesRecord().get("Test3Tracker1"), 0.1);
    }

    // Helper method to simulate user input range values
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
