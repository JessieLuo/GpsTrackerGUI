import nz.sodium.Cell;
import nz.sodium.StreamSink;
import nz.sodium.Unit;
import org.junit.Test;
import swidgets.SButton;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GpsFilterEventRange_Test {
    /* Test filter events range */
    @Test
    public void testEventLatitudeRange() throws NoSuchFieldException, IllegalAccessException {
        // Set up range values for latitude filtering
        List<Cell<Optional<Double>>> rangeVals = createRangeVals(50.0, -50.0, 110.0, -110.0);

        // Set up SButton and StreamSink
        SButton setButton = new SButton("Set");
        StreamSink<GpsEvent> gpsStream = new StreamSink<>();

        // Call filteredEvents with the setButton and gpsStream
        List<Cell<String>> filteredCells = GpsGUI.filteredEvents(rangeVals, setButton, 5000L, gpsStream);

        // Simulate button click to apply the ranges
        simulateButtonClick(setButton);

        // Send GPS events and assert results
        gpsStream.send(new GpsEvent("Tracker1", 45.0, -75.0, 100.0)); // Latitude in range
        assertFalse(filteredCells.get(1).sample().isEmpty()); // due to in the range, it should be included in the panel

        gpsStream.send(new GpsEvent("Tracker2", 55.0, -75.0, 100.0)); // Latitude out of range
        assertTrue(filteredCells.get(1).sample().isEmpty()); // due to out of range, it should not include in the panel
    }

    @Test
    public void testEventLongitudeRange() throws NoSuchFieldException, IllegalAccessException {
        List<Cell<Optional<Double>>> rangeVals = createRangeVals(50.0, -60.0, 70.0, -70.0);

        SButton setButton = new SButton("Set");
        StreamSink<GpsEvent> gpsStream = new StreamSink<>();
        List<Cell<String>> filteredCells = GpsGUI.filteredEvents(rangeVals, setButton, 5000L, gpsStream);

        simulateButtonClick(setButton);

        gpsStream.send(new GpsEvent("Tracker1", 45.0, -65.0, 100.0)); // Longitude in range
        assertFalse(filteredCells.get(1).sample().isEmpty());

        gpsStream.send(new GpsEvent("Tracker2", 45.0, 75.0, 100.0)); // Longitude out of range
        assertTrue(filteredCells.get(1).sample().isEmpty()); // due to out of range, it should not include in the panel
    }

    @Test
    public void testEventLatAndLonRange() throws NoSuchFieldException, IllegalAccessException {
        List<Cell<Optional<Double>>> rangeVals = createRangeVals(60.0, 40.0, -70.0, -80.0);

        SButton setButton = new SButton("Set");
        StreamSink<GpsEvent> gpsStream = new StreamSink<>();
        List<Cell<String>> filteredCells = GpsGUI.filteredEvents(rangeVals, setButton, 5000L, gpsStream);

        simulateButtonClick(setButton);

        gpsStream.send(new GpsEvent("Tracker1", 45.0, -75.0, 100.0)); // Both lat and lon in range
        assertFalse(filteredCells.get(1).sample().isEmpty());

        gpsStream.send(new GpsEvent("Tracker2", 5.0, -85.0, 100.0)); // Both lat and lon out of range
        assertTrue(filteredCells.get(1).sample().isEmpty()); // due to out of range, it should not include in the panel
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
