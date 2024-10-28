import nz.sodium.Cell;
import nz.sodium.Stream;
import nz.sodium.StreamSink;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class GpsTenSimpleTracker_Test {
    @Test
    public void testZeroTrackers() {
        @SuppressWarnings("unchecked")
        Stream<GpsEvent>[] gpsEvents = new Stream[0];

        List<List<Cell<String>>> frpCells = EventProcessor.simplifiedTrackers(gpsEvents);

        // Since there are no trackers, so expect empty lists for each attribute
        assertEquals(0, frpCells.get(0).size());
        assertEquals(0, frpCells.get(1).size());
        assertEquals(0, frpCells.get(2).size());
    }

    @Test
    public void testSingleTracker() {
        StreamSink<GpsEvent> gpsEvent1 = new StreamSink<>();
        @SuppressWarnings("unchecked")
        Stream<GpsEvent>[] gpsEvents = new Stream[]{gpsEvent1};

        List<List<Cell<String>>> frpCells = EventProcessor.simplifiedTrackers(gpsEvents);

        gpsEvent1.send(new GpsEvent("tracker1", 34.05, -118.25, 100)); // Altitude excluded

        // Check if cell exactly store received event
        assertEquals("tracker1", frpCells.get(0).get(0).sample());
        assertEquals("34.05", frpCells.get(1).get(0).sample());
        assertEquals("-118.25", frpCells.get(2).get(0).sample());

        // Ensure only 1 list of attributes
        assertEquals(1, frpCells.get(0).size());
    }

    @Test
    public void testMultipleTracker() {
        StreamSink<GpsEvent> gpsEvent1 = new StreamSink<>();
        StreamSink<GpsEvent> gpsEvent2 = new StreamSink<>();
        @SuppressWarnings("unchecked")
        Stream<GpsEvent>[] gpsEvents = new Stream[]{gpsEvent1, gpsEvent2};

        List<List<Cell<String>>> frpCells = EventProcessor.simplifiedTrackers(gpsEvents);

        // Send more than 1 events
        gpsEvent1.send(new GpsEvent("tracker1", 34.05, -118.25, 44.5));
        gpsEvent2.send(new GpsEvent("tracker2", 40.71, -74.01, 32.0));

        // Verify that the cells hold the expected values
        assertEquals("tracker1", frpCells.get(0).get(0).sample()); // trackerId for gpsEvent1
        assertEquals("34.05", frpCells.get(1).get(0).sample());    // latitude for gpsEvent1
        assertEquals("-118.25", frpCells.get(2).get(0).sample());  // longitude for gpsEvent1

        assertEquals("tracker2", frpCells.get(0).get(1).sample()); // trackerId for gpsEvent2
        assertEquals("40.71", frpCells.get(1).get(1).sample());    // latitude for gpsEvent2
        assertEquals("-74.01", frpCells.get(2).get(1).sample());   // longitude for gpsEvent2
    }

    @Test
    public void testTrackerWithoutAlt() {
        // Test with a single tracker
        StreamSink<GpsEvent> gpsEvent1 = new StreamSink<>();
        @SuppressWarnings("unchecked")
        Stream<GpsEvent>[] gpsEvents = new Stream[]{gpsEvent1};

        List<List<Cell<String>>> frpCells = EventProcessor.simplifiedTrackers(gpsEvents);

        gpsEvent1.send(new GpsEvent("tracker1", 34.05, -118.25, 100)); // Altitude excluded

        // Check that only latitude and longitude are displayed correctly
        assertEquals("tracker1", frpCells.get(0).get(0).sample());
        assertEquals("34.05", frpCells.get(1).get(0).sample());
        assertEquals("-118.25", frpCells.get(2).get(0).sample());

        // Ensure no more attributes such as altitude are present
        assertEquals(3, frpCells.size()); // Only three lists: trackerId, latitude, longitude
    }

}
