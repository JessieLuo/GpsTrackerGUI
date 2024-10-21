import nz.sodium.Cell;
import nz.sodium.CellLoop;
import nz.sodium.Stream;
import nz.sodium.Transaction;
import swidgets.SLabel;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class GuiOutline {
    // Formatter to display time in HH:mm:ss format
    private static final DateTimeFormatter TIME_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
    private final JFrame frame = new JFrame("GPS Tracking Application");
    private final Stream<GpsEvent>[] gpsStreams;

    public GuiOutline(Stream<GpsEvent>[] gpsStreams) {
        // Initialize the GUI components
        this.gpsStreams = gpsStreams;
        initializeComponents();
    }

    private void initializeComponents() {
        // Set up the main frame
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800); // Proper window size to accommodate components

        // 1. All Tracker Display Panel and Real-time Info
        JPanel allTrackerDisplayPanel = TrackerDisplayPanel("All Tracker Display", gpsStreams);
        // Merge all streams into one stream for the current event panel
        Stream<GpsEvent> currentEventStream = mergeAllStreams(gpsStreams);
        JPanel currentTrackerPanel = CurrentTrackerPanel(currentEventStream);

        // 2. Filtered Tracker Display Panel and Control Panel
        JPanel filteredTrackerDisplayPanel = TrackerDisplayPanel("Filtered Tracker Display", gpsStreams);
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
    public JPanel TrackerDisplayPanel(String title, Stream<GpsEvent>[] eventStreams) {
        int columnCount = 3; // Columns: Tracker ID, Lat, Lon
        int rowCount = eventStreams.length;
        JPanel panel = new JPanel(new GridLayout(rowCount + 1, columnCount, 5, 5)); // +1 for the header row
        panel.setBorder(BorderFactory.createTitledBorder(title));

        // Header row
        panel.add(new JLabel("ID"));
        panel.add(new JLabel("Latitude"));
        panel.add(new JLabel("Longitude"));

        // Data rows for each stream
        for (Stream<GpsEvent> evStream : eventStreams) {
            addTrackerLabelsToPanel(evStream, panel);
        }

        return panel;
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

    // Create a panel to display the current tracker event with clear mechanism after 3 seconds
    public JPanel CurrentTrackerPanel(Stream<GpsEvent> gpsEventStream) {
        // Create a panel for real-time tracker information
        JPanel panel = new JPanel(new GridLayout(1, 4, 5, 5)); // Single row for real-time data
        panel.setBorder(BorderFactory.createTitledBorder("Current Event"));

        Transaction.runVoid(() -> {
            // Hold the current GpsEvent in a Cell to track real-time changes
            CellLoop<GpsEvent> gpsEventCell = new CellLoop<>();
            gpsEventCell.loop(gpsEventStream.hold(null));

            // Create reactive Cells for each field (Tracker ID, Latitude, Longitude, Timestamp)
            List<Cell<String>> cells = createBasicFields(gpsEventCell);
            Cell<String> timeCell = gpsEventCell.map(ev -> ev != null ? LocalDateTime.now().format(TIME_FORMATTER) : ""); // Timestamp

            // Create SLabels to display the reactive Cells
            SLabel trackerIdLabel = new SLabel(cells.get(0));
            SLabel latLabel = new SLabel(cells.get(1));
            SLabel lonLabel = new SLabel(cells.get(2));
            SLabel timeLabel = new SLabel(timeCell);

            // Add labels and real-time SLabels to display the current GPS event data
            panel.add(trackerIdLabel);
            panel.add(latLabel);
            panel.add(lonLabel);
            panel.add(timeLabel);

            // Set up the 3-second timer to clear the display if no new event arrives
            Timer[] clearTimer = {null}; // Reference to the active timer

            gpsEventStream.listen(ev -> {
                // Cancel the previous timer if still running
                if (clearTimer[0] != null) {
                    clearTimer[0].stop();
                }

                // Make sure fields are updated when a new event arrives
                trackerIdLabel.setText(ev.name);
                latLabel.setText(Double.toString(ev.latitude));
                lonLabel.setText(Double.toString(ev.longitude));
                timeLabel.setText(LocalDateTime.now().format(TIME_FORMATTER));

                // Create and start a new 3-second timer to clear the display
                clearTimer[0] = new Timer(3000, e -> {
                    // Ensure we only clear the fields if no new event has arrived
                    if (!trackerIdLabel.getText().equals(ev.name)) {
                        return; // A new event has arrived; don't clear the fields
                    }
                    timeLabel.setText("");
                    latLabel.setText("");
                    lonLabel.setText("");
                    trackerIdLabel.setText("");
                });
                clearTimer[0].setRepeats(false); // Ensure the timer runs only once
                clearTimer[0].start();
            });
        });

        return panel;
    }

    // Helper method to merge all event streams into one
    private Stream<GpsEvent> mergeAllStreams(Stream<GpsEvent>[] gpsStreams) {
        if (gpsStreams.length == 0) {
            throw new IllegalArgumentException("No streams provided.");
        }

        // Start with the first stream
        Stream<GpsEvent> mergedStream = gpsStreams[0];
        // Use a loop to merge each stream into the mergedStream
        for (int i = 1; i < gpsStreams.length; i++) {
            mergedStream = mergedStream.orElse(gpsStreams[i]);
        }

        return mergedStream;
    }

    // Helper method to create three Basic info: ID, Lat, Lon
    private List<Cell<String>> createBasicFields(Cell<GpsEvent> gpsEventCell) {
        // Create Cells for Tracker ID, Latitude, Longitude
        Cell<String> trackerIdCell = gpsEventCell.map(ev -> ev != null ? ev.name : ""); // Tracker No
        Cell<String> latCell = gpsEventCell.map(ev -> ev != null ? Double.toString(ev.latitude) : ""); // Latitude
        Cell<String> lonCell = gpsEventCell.map(ev -> ev != null ? Double.toString(ev.longitude) : ""); // Longitude

        return Arrays.asList(trackerIdCell, latCell, lonCell);
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
