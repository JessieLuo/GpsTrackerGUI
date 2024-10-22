import nz.sodium.Cell;
import nz.sodium.Stream;
import nz.sodium.Transaction;
import swidgets.SLabel;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class GuiOutline {
    // Formatter to display time in HH:mm:ss format
    private static final DateTimeFormatter TIME_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
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
        JPanel panel = new JPanel(new GridLayout(1, 5, 5, 5)); // Single row for real-time data
        panel.setBorder(BorderFactory.createTitledBorder("Current Event"));

        Transaction.runVoid(() -> {
            // merge all coming in events as last event
            Stream<GpsEvent> lastGpsStream = gpsEvents[0];
            for (int i = 1; i < gpsEvents.length; i++) {
                lastGpsStream = lastGpsStream.orElse(gpsEvents[i]);
            }

            Cell<Long> eventTime = lastGpsStream
                    .snapshot(new Cell<>(System.currentTimeMillis()))
                    .hold(System.currentTimeMillis());

            // Need 4 cell for each element: id, lat, lon, time
            Cell<String> trackerID = lastGpsStream.map(ev->ev.name).hold("");
            Cell<String> latitude = lastGpsStream.map(ev->String.valueOf(ev.latitude)).hold("");
            Cell<String> longitude = lastGpsStream.map(ev->String.valueOf(ev.longitude)).hold("");
            Cell<Long> currentTime = lastGpsStream
                    .map(time -> System.currentTimeMillis()) // Store system time as long (milliseconds)
                    .hold(System.currentTimeMillis());
            Cell<String> currentTimeStr = currentTime
                    .map(timeInMillis -> {
                        LocalTime currTim = Instant.ofEpochMilli(timeInMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalTime();
                        return currTim.format(TIME_FORMATTER);
                    });

            Cell<Boolean> isOutdated = eventTime.lift(currentTime, (a, b)->(a-b)>3000);

            // Conditionally display or clear GPS data based on whether the event is outdated
            Cell<String> displayTrackerID = isOutdated.lift(trackerID, (outdated, id) -> outdated ? "" : id);
            Cell<String> displayLatitude = isOutdated.lift(latitude, (outdated, lat) -> outdated ? "" : lat);
            Cell<String> displayLongitude = isOutdated.lift(longitude, (outdated, lon) -> outdated ? "" : lon);
            Cell<String> displayCurrentTime = isOutdated.lift(currentTimeStr, (outdated, timeStr) -> outdated ? "" : timeStr);

            SLabel trackerIDLabel = new SLabel(displayTrackerID);
            SLabel latitudeLabel = new SLabel(displayLatitude);
            SLabel longitudeLabel = new SLabel(displayLongitude);
            SLabel currentTimeLabel = new SLabel(displayCurrentTime);

            panel.add(trackerIDLabel);
            panel.add(latitudeLabel);
            panel.add(longitudeLabel);
            panel.add(currentTimeLabel);
        });

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
