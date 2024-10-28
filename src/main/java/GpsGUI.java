import nz.sodium.Cell;
import nz.sodium.Stream;
import nz.sodium.Transaction;
import swidgets.SButton;
import swidgets.SLabel;
import swidgets.STextField;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GpsGUI is the graphical user interface for displaying real-time GPS tracking data across multiple trackers. <br><br>
 * <p>
 * This GUI includes:
 * <ul>
 *   <li><b>Tracker Display:</b> A simplified view showing each tracker's ID, latitude, and longitude, with altitude data removed. This view automatically updates with new GPS events.</li>
 *   <li><b>Current Event Display:</b> Displays the most recent GPS event as a single entry showing ID, latitude, longitude, and timestamp, and clears automatically if not updated within 3 seconds.</li>
 *   <li><b>Filtered Events Display:</b> Shows only events within a specified latitude and longitude range, set by the user. For each tracker, cumulative distance traveled within the last 5 minutes is displayed.</li>
 *   <li><b>Control Panel:</b> Allows users to define latitude and longitude restrictions. Includes input fields for setting maximum and minimum latitude and longitude values, and a button to apply the settings. The panel also shows the current range settings for visual reference.</li>
 * </ul>
 * <p>
 * This class leverages Sodium FRP and custom widgets (swidgets) to manage the interactive elements, user
 * input, and live updates based on GPS event data.
 */
public class GpsGUI {
    private static SButton setButton = new SButton(""); // Set the update restriction button
    private final JFrame frame = new JFrame("GPS Tracking Application"); // The main frame include all panels
    private final Stream<GpsEvent>[] gpsEvents;
    private final List<Cell<Optional<Double>>> rangeVals = new ArrayList<>(); // Receive user inputs
    private final int eventCount; // define tracker display panel rows
    @SuppressWarnings("FieldCanBeLocal")
    private final long windowSizeMillis = 30000; // It could be modified when testing
    // user input fields
    private final STextField latMax = new STextField("", 15);
    private final STextField latMin = new STextField("", 15);
    private final STextField lonMax = new STextField("", 15);
    private final STextField lonMin = new STextField("", 15);

    public GpsGUI(Stream<GpsEvent>[] gpsEvents) {
        this.gpsEvents = gpsEvents;
        this.eventCount = gpsEvents.length;
        initializeComponents();
    }

    /**
     * Main Class to start the app
     */
    public static void main(String[] args) {
        // Initialize the GPS Service
        GpsService gpsService = new GpsService();

        // Retrieve the event streams from GpsService
        Stream<GpsEvent>[] gpsStreams = gpsService.getEventStreams();

        // Display the GUI
        GpsGUI gui = new GpsGUI(gpsStreams);
        gui.show();
    }

    // combine all panels together
    private void initializeComponents() {
        // Set up the main frame
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800);

        // Left-side GUI: Single Display(1) -- Ten simplified Trackers & Single Entry with Time
        JPanel simplifyTrackersDisplayPanel = SimplifyDisplayPanel("All Tracker Display");
        JPanel currentTrackerPanel = CurrentTrackerPanel("Current Tracker Display");
        // Combine the display(1)
        JSplitPane TrackerSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, simplifyTrackersDisplayPanel, currentTrackerPanel);
        TrackerSplitPane.setResizeWeight(0.8);

        // Right-side GUI: Single Display(2) -- Define Range and show result & Display filtered tracker with distance
        JPanel filteredTrackerDisplayPanel = FilteredTrackerDisplayPanel("Filtered Tracker Display");

        // Place the main panels side by side
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Add the left component
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.4;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(TrackerSplitPane, gbc);

        // Add the right component
        gbc.gridx = 1;
        gbc.weightx = 0.6;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(filteredTrackerDisplayPanel, gbc);

        frame.setLayout(new BorderLayout());
        frame.add(mainPanel, BorderLayout.CENTER);
    }

    /**
     * Single Display (1) GUI -- Part I Ten simplifier tracker
     * <p>
     * Displays a simplified view of trackers, showing only the ID, latitude, and longitude of each tracker.
     *
     * @param title Panel title for the tracker display
     * @return JPanel containing tracker display information
     */
    public JPanel SimplifyDisplayPanel(String title) {
        List<List<Cell<String>>> simplyInfo = EventProcessor.simplifiedTrackers(gpsEvents);

        return SimplifyDisplayGUI(title, simplyInfo);
    }

    // set GUI for simplify display
    private JPanel SimplifyDisplayGUI(String title, List<List<Cell<String>>> cells) {
        int columnCount = 3; // Columns: Tracker ID, Latitude, Longitude
        int rowCount = cells.get(0).size();

        JPanel panel = new JPanel(new GridLayout(rowCount + 1, columnCount, 5, 5)); // +1 for header row
        panel.setBorder(BorderFactory.createTitledBorder(title));

        // Add header labels
        panel.add(new JLabel("ID"));
        panel.add(new JLabel("Latitude"));
        panel.add(new JLabel("Longitude"));

        // Bind each Cell list to SLabel components and add them to the panel
        for (int i = 0; i < rowCount; i++) {
            SLabel idLabel = new SLabel(cells.get(0).get(i));
            SLabel latLabel = new SLabel(cells.get(1).get(i));
            SLabel lonLabel = new SLabel(cells.get(2).get(i));

            panel.add(idLabel);
            panel.add(latLabel);
            panel.add(lonLabel);
        }

        return panel;
    }

    /**
     * Single Display (1) GUI -- Part II Current Event coming in
     *
     * @param title Panel title for the current tracker display
     * @return showing current event details
     */
    public JPanel CurrentTrackerPanel(String title) {
        JPanel panel = CurrTrackerGUI(title);

        Transaction.runVoid(() -> {
            // Step 2: Set up the FRP logic and get the content cell
            Cell<String> content = EventProcessor.currentTracker(gpsEvents);

            // Step 3: Bind the content cell to an SLabel and add it to the panel
            SLabel currentEventTexts = new SLabel(content);
            panel.add(currentEventTexts);
        });

        return panel;
    }

    // set gui for current tracker display
    private JPanel CurrTrackerGUI(String title) {
        JPanel panel = new JPanel(new GridLayout(1, 1, 5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    /**
     * Single Display (2)
     * <p>
     * Combines the filtered tracker display with user-defined input controls for setting latitude and longitude ranges.
     *
     * @param title Panel title for the filtered tracker display
     * @return JPanel containing filtered tracker display with control input settings
     */
    public JPanel FilteredTrackerDisplayPanel(String title) {
        JSplitPane controlPanel = ControlGuiWithPanel();

        JPanel fEventDisplayPanel = FilterEvDisplayPanel(title);

        JPanel mainPanel = new JPanel(new BorderLayout());

        // Create JSplitPane with FilterEvDisplayPanel and ControlGuiWithPanel
        JSplitPane vertSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, fEventDisplayPanel, controlPanel);
        vertSplitPane.setResizeWeight(0.8);

        // Add the split pane to the main panel
        mainPanel.add(vertSplitPane, BorderLayout.CENTER);

        return mainPanel;
    }

    /* Single Display (2) GUI -- Part I user input textFiles, result label and set button */
    private JSplitPane ControlGuiWithPanel() {
        // Create ControlGuiWithPanel main outline
        JSplitPane controlPanel;

        JPanel leftPanel = ControlLeftPanel();
        JPanel rightPanel = ControlRightPanel();

        // Combine left-sub and right-sub to control panel (includes input with labels, button, and result)
        controlPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        controlPanel.setDividerLocation(500);
        controlPanel.setResizeWeight(0.5);
        controlPanel.setBorder(BorderFactory.createTitledBorder("Restrict value range"));

        return controlPanel;
    }

    // Helper method to create the left panel
    private JPanel ControlLeftPanel() {
        JPanel leftPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcLeft = new GridBagConstraints();
        gbcLeft.insets = new Insets(10, 10, 10, 10);
        gbcLeft.fill = GridBagConstraints.HORIZONTAL;

        // Define labels for each text field
        JLabel[] fieldLabels = {
                new JLabel("LatitudeMax(-90, 90)"),
                new JLabel("LatitudeMin(-90, 90)"),
                new JLabel("LongitudeMax(-180, 180)"),
                new JLabel("LongitudeMin(-180, 180)")
        };

        // Define the individual text fields to be used in each row
        STextField[] textFieldsArray = {latMax, latMin, lonMax, lonMin};

        // Add labels and individual text fields to the panel
        for (int i = 0; i < textFieldsArray.length; i++) {
            gbcLeft.gridx = 0;
            gbcLeft.gridy = i;
            gbcLeft.anchor = GridBagConstraints.EAST;
            leftPanel.add(fieldLabels[i], gbcLeft);

            gbcLeft.gridx = 1;
            gbcLeft.anchor = GridBagConstraints.WEST;
            leftPanel.add(textFieldsArray[i], gbcLeft);

            // Store user input values with appropriate ranges directly in rangeVals
            if (i < 2) {
                rangeVals.add(Utils.convertInputs(textFieldsArray[i], -90, 90)); // Latitude fields
            } else {
                rangeVals.add(Utils.convertInputs(textFieldsArray[i], -180, 180)); // Longitude fields
            }
        }

        // Add setting button, configured with validation
        setButton = new SButton("Set", inputValid());
        gbcLeft.gridx = 0;
        gbcLeft.gridy = textFieldsArray.length;
        gbcLeft.gridwidth = 2;
        gbcLeft.anchor = GridBagConstraints.SOUTH;
        leftPanel.add(setButton, gbcLeft);

        return leftPanel;
    }

    // Validate inputs
    private Cell<Boolean> inputValid() {
        Cell<Boolean> allValid = new Cell<>(true);
        for (Cell<Optional<Double>> rangeVal : rangeVals) {
            Cell<Boolean> noEmptyValid = rangeVal.map(Optional::isPresent);
            allValid = allValid.lift(noEmptyValid, (a, b) -> a && b);
        }
        Cell<Boolean> minMaxValid = rangeVals.get(0).lift(rangeVals.get(1), rangeVals.get(2), rangeVals.get(3), (a, b, c, d) ->
                a.isPresent() && b.isPresent() && c.isPresent() && d.isPresent() &&
                        a.get() > b.get() && c.get() > d.get());
        Cell<Boolean> rangeValid = rangeVals.get(0).lift(rangeVals.get(1), rangeVals.get(2), rangeVals.get(3), (a, b, c, d) ->
                a.isPresent() && b.isPresent() && c.isPresent() && d.isPresent() &&
                        a.get() >= -90 && b.get() <= 90 && c.get() >= -180 && d.get() <= 180);

        // only all input values valid, the button can be clicked
        allValid = allValid.lift(minMaxValid, rangeValid, (a, b, c) -> a && b && c);

        return allValid;
    }

    // Helper method to create the right panel
    private JPanel ControlRightPanel() {
        JPanel rightPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcRight = new GridBagConstraints();
        gbcRight.insets = new Insets(5, 5, 5, 5);
        gbcRight.fill = GridBagConstraints.HORIZONTAL;

        // Add result label
        Stream<String> storeResult = setButton.sClicked
                .snapshot(latMin.text.lift(latMax.text, lonMin.text, lonMax.text, (a, b, c, d) ->
                        String.format("Latitude(%s, %s) Longitude(%s, %s)", a, b, c, d)));
        Cell<String> result = storeResult.hold("Input must: numeric(include -); max > min");
        SLabel resultLabel = new SLabel(result);
        gbcRight.gridx = 0;
        gbcRight.gridy = 1;
        gbcRight.anchor = GridBagConstraints.CENTER;
        rightPanel.add(resultLabel, gbcRight);

        return rightPanel;
    }

    /* Single Display (2) GUI -- Part II show filtered trackers' info: id, lat, lon, time, distance (each 5-min update) */
    private JPanel FilterEvDisplayPanel(String title) {
        // Create FilterEvDisplayPanel
        JPanel displayPanel = FilterEvDisplayGUI(title);

        // Dynamically output result on GUI
        for (Stream<GpsEvent> gpsEvent : gpsEvents) {
            /* Core event-drive logic */
            List<Cell<String>> results = EventProcessor.filteredEvents(rangeVals, setButton, windowSizeMillis, gpsEvent);

            // Add GUI elements to correspond panel
            SLabel filterId = new SLabel(results.get(0));
            SLabel filterLat = new SLabel(results.get(1));
            SLabel filterLon = new SLabel(results.get(2));
            SLabel filterTime = new SLabel(results.get(3));
            SLabel filterDist = new SLabel(results.get(4));

            displayPanel.add(filterId);
            displayPanel.add(filterLat);
            displayPanel.add(filterLon);
            displayPanel.add(filterTime);
            displayPanel.add(filterDist);
        }

        return displayPanel;
    }

    // set gui for filtered tracker display
    private JPanel FilterEvDisplayGUI(String title) {
        JPanel displayPanel = new JPanel(new GridLayout(eventCount + 1, 5, 5, 5));
        displayPanel.setBorder(BorderFactory.createTitledBorder(title));
        displayPanel.add(new JLabel("ID"));
        displayPanel.add(new JLabel("Latitude"));
        displayPanel.add(new JLabel("Longitude"));
        displayPanel.add(new JLabel("Time"));
        displayPanel.add(new JLabel("Distance"));

        return displayPanel;
    }

    public void show() {
        frame.setVisible(true);
    }

}
