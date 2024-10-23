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
import java.util.Objects;

public class GuiOutline {
    private final JFrame frame = new JFrame("GPS Tracking Application");
    private final Stream<GpsEvent>[] gpsEvents;
    private final int rowCount;

    public GuiOutline(Stream<GpsEvent>[] gpsEvents) {
        this.gpsEvents = gpsEvents;
        this.rowCount = gpsEvents.length;
        initializeComponents();
    }

    private void initializeComponents() {
        // Set up the main frame
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800);

        // Left-side GUI: Single Display(1) -- Ten simplified Trackers & Single Entry with Time
        JPanel allTrackerDisplayPanel = TrackerDisplayPanel("All Tracker Display", gpsEvents);
        JPanel currentTrackerPanel = CurrentTrackerPanel("Current Tracker Display", gpsEvents);
        // Combine the display(1)
        JSplitPane allTrackerSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, allTrackerDisplayPanel, currentTrackerPanel);
        allTrackerSplitPane.setResizeWeight(0.8);

        // Right-side GUI: Single Display(2) --
        JPanel filteredTrackerDisplayPanel = FilteredTrackerDisplayPanel("Filtered Tracker Display", gpsEvents);
        JPanel controlPanel = ControlPanel();
        // Combine Display(2)
        JSplitPane filteredTrackerSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filteredTrackerDisplayPanel, controlPanel);
        filteredTrackerSplitPane.setResizeWeight(0.8);

        // Place the main panels side by side
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Add the left component
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.6;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(allTrackerSplitPane, gbc);

        // Add the right component
        gbc.gridx = 1;
        gbc.weightx = 0.4;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(filteredTrackerSplitPane, gbc);

        frame.setLayout(new BorderLayout());
        frame.add(mainPanel, BorderLayout.CENTER);
    }

    // Create a panel to display all tracker events
    public JPanel TrackerDisplayPanel(String title, Stream<GpsEvent>[] gpsEvents) {
        int columnCount = 3; // Columns: Tracker ID, Lat, Lon
        JPanel panel = new JPanel(new GridLayout(rowCount + 1, columnCount, 5, 5)); // +1 for the header row
        panel.setBorder(BorderFactory.createTitledBorder(title));

        panel.add(new JLabel("ID"));
        panel.add(new JLabel("Latitude"));
        panel.add(new JLabel("Longitude"));

        // Data rows for each stream
        for (Stream<GpsEvent> evStream : gpsEvents) {
            Cell<String> trackerId = evStream.map(ev -> ev.name).hold("");
            Cell<String> latitude = evStream.map(ev -> String.valueOf(ev.latitude)).hold("");
            Cell<String> longitude = evStream.map(ev -> String.valueOf(ev.longitude)).hold("");

            SLabel id = new SLabel(trackerId);
            SLabel lat = new SLabel(latitude);
            SLabel lon = new SLabel(longitude);

            panel.add(id);
            panel.add(lat);
            panel.add(lon);
        }

        return panel;
    }

    public JPanel CurrentTrackerPanel(String title, Stream<GpsEvent>[] gpsEvents) {
        JPanel panel = new JPanel(new GridLayout(1, 1, 5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(title));

        Transaction.runVoid(() -> {
            // Capture system time
            TimerSystem<Long> timerSystem = new MillisecondsTimerSystem();
            Cell<Long> timer = timerSystem.time;

            // merge all coming in events as last event
            Stream<GpsEvent> lastGpsStream = gpsEvents[0];
            for (int i = 1; i < 2; i++) {
                lastGpsStream = lastGpsStream.orElse(gpsEvents[i]);
            }

            // Get the event occurs time
            Cell<String> formattedCurrTime = lastGpsStream
                    .snapshot(timer).hold(System.currentTimeMillis())
                    .map(timeInMillions -> ", Time: " + formatTime(timeInMillions));

            // Basic content in current display panel
            Cell<String> content = lastGpsStream.map(ev -> ev.name + ", Latitude:" + ev.latitude + ", Longitude: " + ev.longitude).hold("").lift(formattedCurrTime, (l, r) -> l + r);

            // clean content when it outdated
            Cell<String> clean = new Cell<>("");

            // Stream to capture changes in trackerID, latitude, and longitude
            Stream<GpsData> sCurrentGpsData = lastGpsStream.map(ev -> new GpsData(ev.name, String.valueOf(ev.latitude), String.valueOf(ev.longitude), System.currentTimeMillis()));

            // CellLoop to track the previous state
            CellLoop<GpsData> previousGpsData = new CellLoop<>();

            // Stream to detect changes in the GPS data
            Stream<GpsData> sChanged = sCurrentGpsData.snapshot(previousGpsData, (curr, prev) -> !curr.equals(prev) ? curr : null);

            // Only update when data change
            Stream<GpsData> sUpdate = sChanged.filter(Objects::nonNull);

            // update the previous data when it changed
            previousGpsData.loop(sUpdate.hold(new GpsData("", "", "", 0L)));

            // Stream to check if content has remained unchanged for 3 seconds
            Stream<Long> sLastChangeTime = sUpdate.snapshot(timer, (changed, currentTime) -> currentTime);

            // Cell to hold the last change time
            Cell<Long> lastChangeTimeValue = sLastChangeTime.hold(0L);

            // Check if 3 seconds have passed since the last content change
            Cell<Boolean> isOutdated = timer.lift(lastChangeTimeValue, (currentTime, lastChangeTime) -> (currentTime - lastChangeTime) >= 3000);

            // Display real content; otherwise, display clean
            Cell<String> displayContent = isOutdated.lift(content, (unchanged, cont) -> unchanged ? clean.sample() : cont);

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

    public JPanel FilteredTrackerDisplayPanel(String title, Stream<GpsEvent>[] gpsEvents) {
        JPanel panel = new JPanel(new GridLayout(rowCount + 1, 5, 5, 5)); // +1 for the header row
        panel.setBorder(BorderFactory.createTitledBorder(title));

        panel.add(new JLabel("ID"));
        panel.add(new JLabel("Latitude"));
        panel.add(new JLabel("Longitude"));
        panel.add(new JLabel("Time"));
        panel.add(new JLabel("Distance"));

        TimerSystem<Long> timerSystem = new MillisecondsTimerSystem();
        Cell<Long> timer = timerSystem.time;

        for (Stream<GpsEvent> evStream : gpsEvents) {
            Cell<String> trackerId = evStream.map(ev -> ev.name).hold("");
            Cell<String> latitude = evStream.map(ev -> String.valueOf(ev.latitude)).hold("");
            Cell<String> longitude = evStream.map(ev -> String.valueOf(ev.longitude)).hold("");
            Cell<String> altitude = evStream.map(ev -> String.valueOf(ev.altitude)).hold("");
            Cell<String> timeStamp = evStream
                    .snapshot(timer).hold(System.currentTimeMillis())
                    .map(this::formatTime);

            SLabel id = new SLabel(trackerId);
            SLabel lat = new SLabel(latitude);
            SLabel lon = new SLabel(longitude);
            SLabel time = new SLabel(timeStamp);
            SLabel distance = new SLabel(altitude);

            panel.add(id);
            panel.add(lat);
            panel.add(lon);
            panel.add(time);
            panel.add(distance);
        }

        return panel;
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
