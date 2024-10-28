import nz.sodium.Cell;
import nz.sodium.Stream;
import nz.sodium.StreamSink;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class GpsCurrEventClean_Test {
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
        GpsEvent event = new GpsEvent("ContentTestTracker1", 34.05, -118.25, 100);
        gpsEvent1.send(event);

        // the content initially should contain the event data
        assertFalse(content.sample().isEmpty());

        // Wait for over 3 seconds to simulate the timeout
        TimeUnit.SECONDS.sleep(4);

        // the content should be cleared after 3 seconds
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

        // Wait for 2 seconds, then send a second event before the 3-second timeout
        TimeUnit.SECONDS.sleep(2);

        GpsEvent secondEvent = new GpsEvent("Tracker2", 40.71, -74.01, 200);
        gpsEvent1.send(secondEvent);
        // Content should still not be empty after the second event
        assertFalse(content.sample().isEmpty());
        assertTrue(content.sample().contains("Tracker2")); // Ensure current event is second
        assertFalse(content.sample().contains("Tracker1")); // Ensure first event be overwritten

        // Wait an additional 2 seconds, verifying the second event reset the timeout
        TimeUnit.SECONDS.sleep(2);

        // Content should not be cleared due to not over 3 seconds
        assertFalse(content.sample().isEmpty());
    }
}
