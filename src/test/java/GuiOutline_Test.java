import nz.sodium.Cell;
import nz.sodium.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import swidgets.SLabel;

import javax.swing.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class GuiOutline_Test {
    private GuiOutline guiOutline;
    private JPanel panel;
    private SLabel trackerIdLabel, latLabel, lonLabel;

    @Before
    public void setUp() throws Exception {
        // Initialize the GPS Service to get real event streams
        GpsService gpsService = new GpsService();

        // Retrieve the event streams from GpsService
        Stream<GpsEvent>[] gpsStreams = gpsService.getEventStreams();

        // Initialize the GuiOutline with the real GPS streams
        guiOutline = new GuiOutline(gpsStreams);

        // Assume the GuiOutline provides access to the current tracker panel
        panel = guiOutline.CurrentTrackerPanel(gpsStreams[0]);  // Use one of the streams

        // Extract the SLabels from the panel for testing
        trackerIdLabel = (SLabel) panel.getComponent(0);
        latLabel = (SLabel) panel.getComponent(1);
        lonLabel = (SLabel) panel.getComponent(2);
    }

    @After
    public void tearDown() throws Exception {
        // Perform any cleanup necessary after each test
        panel = null;
        guiOutline = null;
        trackerIdLabel = null;
        latLabel = null;
        lonLabel = null;

        // Suggestion: Reset state if necessary, but avoid modifying GpsService/GpsEvent
    }


    // Test 1: Fields should be cleared after 3 seconds if no new event arrives
    @Test
    public void testClearAfter3Seconds() throws Exception {
        // Wait for a short while to allow fields to be populated from real GPS events
        TimeUnit.SECONDS.sleep(1);

        // Check if the fields have been populated (functional check, not concrete values)
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(trackerIdLabel.getText().isEmpty());
            assertFalse(latLabel.getText().isEmpty());
            assertFalse(lonLabel.getText().isEmpty());
        });

        // Wait for 4 seconds (longer than the 3-second timeout)
        TimeUnit.SECONDS.sleep(4);

        // Check if the fields have been cleared
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(trackerIdLabel.getText().isEmpty());
            assertTrue(latLabel.getText().isEmpty());
            assertTrue(lonLabel.getText().isEmpty());
        });
    }

    // Test 2: Fields should update before being cleared if a new event arrives within 3 seconds
    @Test
    public void testUpdateBeforeClear() throws Exception {
        // Wait for a short while to allow fields to be populated from real GPS events
        TimeUnit.SECONDS.sleep(1);

        // Check if the fields have been populated (functional check, not concrete values)
        SwingUtilities.invokeAndWait(() -> {
            System.out.println("Test 2 - Initial trackerIdLabel: " + trackerIdLabel.getText());
            assertFalse(trackerIdLabel.getText().isEmpty());
            assertFalse(latLabel.getText().isEmpty());
            assertFalse(lonLabel.getText().isEmpty());
        });

        // simulating new GPS event arriving just before the 3-second timeout
        TimeUnit.SECONDS.sleep(3);

        // Log the state of the fields after 2 seconds to see if they are cleared or updated
        SwingUtilities.invokeAndWait(() -> {
            System.out.println("Test 2 - After 2 seconds: trackerIdLabel: " + trackerIdLabel.getText());
            System.out.println("Test 2 - latLabel: " + latLabel.getText());
            System.out.println("Test 2 - lonLabel: " + lonLabel.getText());
        });

        // Wait for another 2 seconds to ensure the new event has reset the clearing mechanism
        TimeUnit.SECONDS.sleep(3);

        // Ensure that the fields are still not cleared since the new event arrived in time
        SwingUtilities.invokeAndWait(() -> {
            System.out.println("Test 2 - Final check: trackerIdLabel: " + trackerIdLabel.getText());
            assertFalse(trackerIdLabel.getText().isEmpty());
            assertFalse(latLabel.getText().isEmpty());
            assertFalse(lonLabel.getText().isEmpty());
        });
    }
}