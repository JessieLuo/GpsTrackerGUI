import nz.sodium.Cell;
import nz.sodium.CellLoop;
import nz.sodium.Stream;
import nz.sodium.Transaction;
import nz.sodium.time.MillisecondsTimerSystem;
import nz.sodium.time.TimerSystem;
import swidgets.SLabel;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class GuiOutline {
    private final JFrame frame = new JFrame("GPS Tracking Application");
    private final Stream<GpsEvent>[] gpsEvents;

    public GuiOutline(Stream<GpsEvent>[] gpsEvents) {
        // Initialize the GUI components
        this.gpsEvents = gpsEvents;
        initializeComponents();
    }

    private void initializeComponents() {
        // Set up the main frame
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800); // Proper window size to accommodate components

        // All Tracker Display Panel and Real-time Info
        JPanel allTrackerDisplayPanel = TrackerDisplayPanel("All Tracker Display", gpsEvents);
        JPanel currentTrackerPanel = CurrentTrackerPanel(gpsEvents);

        // Filtered Tracker Display Panel and Control Panel
        JPanel filteredTrackerDisplayPanel = TrackerDisplayPanel("Filtered Tracker Display", gpsEvents);
        JPanel controlPanel = ControlPanel();

        // Combine the left display panel with its corresponding info panel using JSplitPane
        JSplitPane allTrackerSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, allTrackerDisplayPanel, currentTrackerPanel);
        allTrackerSplitPane.setResizeWeight(0.8); // Give more space to the display panel

        // Combine the right display panel with the control panel using JSplitPane
        JSplitPane filteredTrackerSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filteredTrackerDisplayPanel, controlPanel);
        filteredTrackerSplitPane.setResizeWeight(0.8); // Give more space to the display panel

        // Place the main panels side by side
        JPanel mainPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        mainPanel.add(allTrackerSplitPane);
        mainPanel.add(filteredTrackerSplitPane);

        frame.setLayout(new BorderLayout());
        frame.add(mainPanel, BorderLayout.CENTER);
    }

    // Create a panel to display all tracker events
    public JPanel TrackerDisplayPanel(String title, Stream<GpsEvent>[] gpsEvents) {
        int columnCount = 3; // Columns: Tracker ID, Lat, Lon
        int rowCount = gpsEvents.length;
        JPanel panel = new JPanel(new GridLayout(rowCount + 1, columnCount, 5, 5)); // +1 for the header row
        panel.setBorder(BorderFactory.createTitledBorder(title));

        // Header row
        panel.add(new JLabel("ID"));
        panel.add(new JLabel("Latitude"));
        panel.add(new JLabel("Longitude"));

        // Data rows for each stream
        for (Stream<GpsEvent> evStream : gpsEvents) {
            addTrackerLabelsToPanel(evStream, panel);
        }

        return panel;
    }

    // Helper method to create three Basic info: ID, Lat, Lon
    private List<Cell<String>> createBasicFields(Cell<GpsEvent> gpsEventCell) {
        // Create Cells for Tracker ID, Latitude, Longitude
        Cell<String> trackerIdCell = gpsEventCell.map(ev -> ev != null ? ev.name : ""); // Tracker No
        Cell<String> latCell = gpsEventCell.map(ev -> ev != null ? Double.toString(ev.latitude) : ""); // Latitude
        Cell<String> lonCell = gpsEventCell.map(ev -> ev != null ? Double.toString(ev.longitude) : ""); // Longitude

        return Arrays.asList(trackerIdCell, latCell, lonCell);
    }

    // Helper method to add labels for a single stream of tracker data
    private void addTrackerLabelsToPanel(Stream<GpsEvent> evStream, JPanel panel) {
        // Hold the current GpsEvent in a Cell
        Cell<GpsEvent> gpsEventCell = evStream.hold(null);

        List<Cell<String>> cells = createBasicFields(gpsEventCell);

        // Create SLabels for the extracted values
        SLabel trackerIdLabel = new SLabel(cells.get(0));
        SLabel latLabel = new SLabel(cells.get(1));
        SLabel lonLabel = new SLabel(cells.get(2));

        // Add the created SLabels to the panel
        panel.add(trackerIdLabel);
        panel.add(latLabel);
        panel.add(lonLabel);
    }

    public JPanel CurrentTrackerPanel(Stream<GpsEvent>[] gpsEvents) {
        JPanel panel = new JPanel(new GridLayout(1, 1, 5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Current Event"));

        Transaction.runVoid(() -> {
            // Capture system time
            TimerSystem<Long> timerSystem = new MillisecondsTimerSystem();
            Cell<Long> timer = timerSystem.time;

            // merge all coming in events as last event
            Stream<GpsEvent> lastGpsStream = gpsEvents[0];
            for (int i = 1; i < gpsEvents.length; i++) {
                lastGpsStream = lastGpsStream.orElse(gpsEvents[i]);
            }

            // Get the event occurs time
            Cell<Long> getEventTime = lastGpsStream
                    .snapshot(timer)
                    .hold(System.currentTimeMillis());
            Cell<String> formattedCurrTime = getEventTime.map(timeInMillions -> ", Time: " + formatTime(timeInMillions));

            // Basic content in current display panel
            Cell<String> content = lastGpsStream.map(ev -> ev.name + ", Latitude:" + ev.latitude + ", Longitude: " + ev.longitude).hold("").lift(formattedCurrTime, (l, r) -> l + r);

            // clean content when it outdated
            Cell<String> clean = new Cell<>("");

            // Stream to capture changes in trackerID, latitude, and longitude
            Stream<GpsData> gpsDataStream = lastGpsStream.map(ev -> new GpsData(ev.name, String.valueOf(ev.latitude), String.valueOf(ev.longitude), System.currentTimeMillis()));

            // CellLoop to track the previous state
            CellLoop<GpsData> previousGpsData = new CellLoop<>();

            // Stream to detect changes in the GPS data
            Stream<Boolean> sChanged = gpsDataStream.snapshot(previousGpsData, (current, previous) -> {
                return !current.equals(previous);  // Compare the current and previous values
            }).filter(changed -> changed);  // Only propagate if the content has changed

            // Loop the previous GpsData to store the latest state
            previousGpsData.loop(gpsDataStream.hold(new GpsData("", "", "", 0L)));

            // Stream to check if content has remained unchanged for 3 seconds
            Stream<Long> sLastChangeTime = sChanged.snapshot(timer, (changed, currentTime) -> currentTime);

            // Cell to hold the last change time
            Cell<Long> lastChangeTimeValue = sLastChangeTime.hold(0L);

            // Check if 3 seconds have passed since the last content change
            Cell<Boolean> isOutdated = timer.lift(lastChangeTimeValue, (currentTime, lastChangeTime) -> {
                System.out.println("Content unchanged time: " + (currentTime-lastChangeTime));
                return (currentTime - lastChangeTime) >= 3000; // Return true if 3 seconds have passed
            });

            // Display either the clean content or actual content based on whether it has been unchanged for 3 seconds
            Cell<String> displayContent = isOutdated.lift(content, (unchanged, cont) -> unchanged ? clean.sample() : cont);

            // Display text
            SLabel currentEventTexts = new SLabel(displayContent);

            panel.add(currentEventTexts);
        });

        return panel;
    }

    // Format time as H:M:S
    private String formatTime(long time) {
        DateTimeFormatter TIME_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        Instant instant = Instant.ofEpochMilli(time);
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return localDateTime.format(TIME_FORMATTER);
    }

    private JPanel ControlPanel() {
        // Create three panels for the three parts
        JPanel inputPanel = UserInputPanel();
        JPanel buttonPanel = ButtonPanel();
        JPanel labelPanel = UserInputsLabelPanel();

        // First split: between inputPanel and buttonPanel
        JSplitPane splitPane1 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputPanel, buttonPanel);
        splitPane1.setResizeWeight(0.6); // More space for input fields

        // Second split: between buttonPanel and labelPanel
        JSplitPane splitPane2 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, splitPane1, labelPanel);
        splitPane2.setResizeWeight(0.8); // More space for button area

        // Wrap in a main control panel
        JPanel mainControlPanel = new JPanel(new BorderLayout());
        mainControlPanel.add(splitPane2, BorderLayout.CENTER);
        mainControlPanel.setBorder(BorderFactory.createTitledBorder("Control Panel"));
        return mainControlPanel;
    }

    private JPanel UserInputPanel() {
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        inputPanel.add(new JLabel("Min Lat:"), gbc);
        gbc.gridx = 1;
        inputPanel.add(CompactTextField(), gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        inputPanel.add(new JLabel("Max Lat:"), gbc);
        gbc.gridx = 1;
        inputPanel.add(CompactTextField(), gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        inputPanel.add(new JLabel("Min Lon:"), gbc);
        gbc.gridx = 1;
        inputPanel.add(CompactTextField(), gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        inputPanel.add(new JLabel("Max Lon:"), gbc);
        gbc.gridx = 1;
        inputPanel.add(CompactTextField(), gbc);

        return inputPanel;
    }

    private JPanel ButtonPanel() {
        JPanel buttonPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.CENTER;
        JButton setRestrictionButton = new JButton("Set");
        buttonPanel.add(setRestrictionButton, gbc);
        return buttonPanel;
    }

    private JPanel UserInputsLabelPanel() {
        JPanel labelPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.CENTER;
        JLabel restrictionValueLabel = new JLabel("Current Restriction: None");
        labelPanel.add(restrictionValueLabel, gbc);
        return labelPanel;
    }

    private JTextField CompactTextField() {
        JTextField textField = new JTextField(8);
        textField.setPreferredSize(new Dimension(80, 25)); // Limit the preferred size
        return textField;
    }

    public void show() {
        frame.setVisible(true);
    }

}

/**
 * Helper class
 */
class GpsData {
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

    public boolean equals(GpsData other) {
        return this.trackerID.equals(other.trackerID) &&
                this.latitude.equals(other.latitude) &&
                this.longitude.equals(other.longitude);
    }
}
