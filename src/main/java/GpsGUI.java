import nz.sodium.*;
import nz.sodium.time.MillisecondsTimerSystem;
import nz.sodium.time.TimerSystem;
import swidgets.SButton;
import swidgets.SLabel;
import swidgets.STextField;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GpsGUI {
    // Record events for specific tracker
    private static final Map<String, Position> positionsRecord = new HashMap<>(); // separate each tracker with it current position info
    private static final Map<String, Double> totalDistancesRecord = new HashMap<>(); // store each tracker travelled distance
    private static final Map<String, Long> totalTimeRecord = new HashMap<>(); // record current event fired time
    private static final Map<String, Double> timeBasedTotalDistRecord = new HashMap<>(); // record the distance only events cumulative pass time equal to intervals
    private static final double FEET_TO_METER = 0.3048; // convert altitude from feet to meter
    // Set the update restriction button
    private static SButton setButton = new SButton("");
    private final JFrame frame = new JFrame("GPS Tracking Application");
    private final Stream<GpsEvent>[] gpsEvents;
    private final int eventCount;
    // Receive user inputs
    private final List<Cell<Optional<Double>>> rangeVals = new ArrayList<>();

    public GpsGUI(Stream<GpsEvent>[] gpsEvents) {
        this.gpsEvents = gpsEvents;
        this.eventCount = gpsEvents.length;
        initializeComponents();
    }

    // Format time as H:M:S
    public static String formatTime(long time) {
        DateTimeFormatter TIME_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        Instant instant = Instant.ofEpochMilli(time);
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

        return localDateTime.format(TIME_FORMATTER);
    }

    /**
     * Calculates the 3D distance between two geographic positions, considering both the
     * Earth's surface (horizontal) distance and altitude difference.
     *
     * <p>This method uses the Haversine formula to compute the horizontal distance
     * (great-circle distance) between two points given their <b>latitude</b> and <b>longitude</b>. It then
     * calculates the <b>altitude</b> difference and combines these values with the Pythagorean
     * theorem to obtain the 3D distance in meters.</p>
     *
     * @param pos1 The first position, containing latitude, longitude, and altitude.
     * @param pos2 The second position, containing latitude, longitude, and altitude.
     * @return The 3D distance between the two positions in meters, or 0.0 if either position is null.
     */
    public static double calculateDistance(Position pos1, Position pos2) {
        if (pos1 == null || pos2 == null) return 0.0;

        // Earth's radius in meters
        double horizontalDistance = getHorizontalDistance(pos1, pos2);

        // Calculate vertical distance (altitude difference)
        double deltaAlt = pos2.altitude - pos1.altitude;

        // Use Pythagorean theorem to calculate the 3D distance
        return Math.sqrt(horizontalDistance * horizontalDistance + deltaAlt * deltaAlt);
    }

    /**
     * Calculates the 2D (horizontal) distance between two positions on Earth's surface
     * using the Haversine formula.
     *
     * <p>The Haversine formula calculates the shortest distance over Earth's surface
     * (great-circle distance) using latitude and longitude coordinates:</p>
     *
     * <pre>
     * a = sin^2(\delta(lat) / 2) + cos(lat1) * cos(lat2) * sin^2(\delta(lon) / 2)
     * c = 2 * atan2(\sqrt(a), \sqrt(1 - a))
     * d = R * c
     * </pre>
     * <p>
     * where:
     * - \delta(lat) = lat2 - lat1 and \delta(lon) = lon2 - lon1
     * - R is Earth's radius in meters (6,371,000 meters)
     *
     * @param pos1 The first position, containing latitude and longitude.
     * @param pos2 The second position, containing latitude and longitude.
     * @return The horizontal distance between the two positions in meters.
     */
    private static double getHorizontalDistance(Position pos1, Position pos2) {
        double lat1 = Math.toRadians(pos1.latitude);
        double lat2 = Math.toRadians(pos2.latitude);
        double deltaLat = Math.toRadians(pos2.latitude - pos1.latitude);
        double deltaLon = Math.toRadians(pos2.longitude - pos1.longitude);

        // Haversine formula
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371000.0 * c;
    }

    // Safely convert user input to double value
    private static Cell<Optional<Double>> convertInputs(STextField textField, double min, double max) {
        return textField.text.map(t -> {
            t = t.trim();
            if (t.isEmpty()) {
                return Optional.empty();
            }
            try {
                double val = Double.parseDouble(t);
                if (val >= min && val <= max) {
                    return Optional.of(val);
                } else {
                    return Optional.empty();
                }
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    /**
     * Process simplified tracker information that without altitude
     *
     * @param gpsEvents All the event carrying Gps data
     * @return A list of tracker info, include list of id, list of lat and list of lon
     */
    public static List<List<Cell<String>>> simplifiedTrackers(Stream<GpsEvent>[] gpsEvents) {
        List<Cell<String>> trackerIds = new ArrayList<>();
        List<Cell<String>> latitudes = new ArrayList<>();
        List<Cell<String>> longitudes = new ArrayList<>();

        for (Stream<GpsEvent> evStream : gpsEvents) {
            trackerIds.add(evStream.map(ev -> ev.name).hold(""));
            latitudes.add(evStream.map(ev -> String.valueOf(ev.latitude)).hold(""));
            longitudes.add(evStream.map(ev -> String.valueOf(ev.longitude)).hold(""));
        }

        List<List<Cell<String>>> cells = new ArrayList<>();
        cells.add(trackerIds);
        cells.add(latitudes);
        cells.add(longitudes);

        return cells;
    }

    /**
     * Process current event and clean after 3 sec if not be overwritten
     *
     * @param gpsEvents All the event carrying Gps data
     * @return Current fired event information: A string of [id, lat, lon, time], or empty string if exceed time
     */
    public static Cell<String> currentTracker(Stream<GpsEvent>[] gpsEvents) {
        return Transaction.run(() -> {
            // Set up the system time stream and hold the latest time in a cell
            StreamSink<Long> sysTimeStream = new StreamSink<>();
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(() -> {
                long currentTime = System.currentTimeMillis();
                sysTimeStream.send(currentTime); // Push current system time into the stream
            }, 0, 1, TimeUnit.SECONDS);

            Cell<Long> sysTimeValue = sysTimeStream.hold(System.currentTimeMillis());

            // Merge all incoming events as the current event stream
            Stream<GpsEvent> lastGpsStream = gpsEvents[0];
            for (int i = 1; i < gpsEvents.length; i++) {
                lastGpsStream = lastGpsStream.orElse(gpsEvents[i]);
            }

            // Record data from the current event and wrap it with a timestamp
            CellLoop<GpsData> currData = new CellLoop<>();
            TimerSystem<Long> timerSystem = new MillisecondsTimerSystem();
            Cell<Long> timer = timerSystem.time;

            Stream<GpsData> sWrapTime = lastGpsStream.snapshot(timer, (ev, t) ->
                    new GpsData(ev.name, String.valueOf(ev.latitude), String.valueOf(ev.longitude), t));
            Stream<GpsData> sSetNew = sWrapTime.snapshot(currData, (ev, c) -> !ev.equals(c) ? ev : null);

            currData.loop(sSetNew.filter(Objects::nonNull).hold(new GpsData("", "", "", 0L)));

            // Clean event if the data has not been updated within 3 seconds
            return sysTimeValue.lift(currData, (sysTime, data) ->
                    (sysTime - data.time > 3000) ? "" : data.toString());
        });
    }

    /**
     * Process to filter event: if current event not met condition, all info would be empty
     *
     * @param inputVals        User input restriction range values: maximum latitude, minimum latitude; maximum longitude, minimum longitude
     * @param setButton        The button could set the range value
     * @param windowSizeMillis Define the distance calculation time interval
     * @param gpsEvent         Current event carrying Gps data
     * @return A list that stored filtered events information: [id, lat, lon, time, dist]
     */
    public static List<Cell<String>> filteredEvents(List<Cell<Optional<Double>>> inputVals, SButton setButton, long windowSizeMillis, Stream<GpsEvent> gpsEvent) {
        TimerSystem<Long> timerSystem = new MillisecondsTimerSystem();
        Cell<Long> timer = timerSystem.time;
        // Only update the restriction when click button
        Cell<Optional<Double>> latMaxAfterClick = setButton.sClicked
                .snapshot(inputVals.get(0), (u, r) -> r).hold(Optional.empty());
        Cell<Optional<Double>> latMinAfterClick = setButton.sClicked
                .snapshot(inputVals.get(1), (u, r) -> r).hold(Optional.empty());
        Cell<Optional<Double>> lonMaxAfterClick = setButton.sClicked
                .snapshot(inputVals.get(2), (u, r) -> r).hold(Optional.empty());
        Cell<Optional<Double>> lonMinAfterClick = setButton.sClicked
                .snapshot(inputVals.get(3), (u, r) -> r).hold(Optional.empty());

        List<Cell<String>> filterResults = new ArrayList<>();

        // Extract value
        Cell<String> id = gpsEvent.map(ev -> ev.name).hold("");
        Cell<Double> lat = gpsEvent.map(ev -> ev.latitude).hold(0.0);
        Cell<Double> lon = gpsEvent.map(ev -> ev.longitude).hold(0.0);
        Cell<Double> alt = gpsEvent.map(ev -> ev.altitude * FEET_TO_METER).hold(0.0); // convert feet to meter
        Cell<Long> time = gpsEvent.snapshot(timer).hold(0L); // event occurs time

        // Start filtering
        Cell<Boolean> isValid = new Cell<>(true);
        Cell<Boolean> latValid = lat.lift(latMaxAfterClick, latMinAfterClick, (evLat, max, min)
                -> max.isPresent() && min.isPresent() && evLat <= max.get() && evLat >= min.get());
        Cell<Boolean> lonValid = lon.lift(lonMaxAfterClick, lonMinAfterClick, (evLon, max, min)
                -> max.isPresent() && min.isPresent() && evLon <= max.get() && evLon >= min.get());
        isValid = isValid.lift(latValid, lonValid, (a, b, c) -> a && b && c);

        // calculate distance
        Cell<Double> dist = isValid.lift(id, lat, lon, alt, time, (valid, pId, p1, p2, p3, t) -> {
            if (valid) {
                Position currentPosition = new Position(p1, p2, p3, t);

                double distance;

                Long timePassed;

                // If current ID exist, add dist to previous
                if (positionsRecord.containsKey(pId)) {
                    Position lastPosition = positionsRecord.get(pId);
                    distance = calculateDistance(lastPosition, currentPosition);

                    // Record each two position distance for the same id
                    totalDistancesRecord.put(pId, totalDistancesRecord.getOrDefault(pId, 0.0) + distance);

                    timePassed = t - lastPosition.time;
                    totalTimeRecord.put(pId, totalTimeRecord.getOrDefault(pId, 0L) + timePassed);

                    // Record the distance only when each defined interval is reached
                    if (totalTimeRecord.getOrDefault(pId, 0L) >= windowSizeMillis) {
                        totalTimeRecord.put(pId, totalTimeRecord.get(pId) - windowSizeMillis); // reset cumulative time
                        timeBasedTotalDistRecord.put(pId, totalDistancesRecord.getOrDefault(pId, 0.0));
                    }
                }

                positionsRecord.put(pId, currentPosition);

                return timeBasedTotalDistRecord.getOrDefault(pId, 0.0);
            }
            return 0.0; // If an event not met condition, its distance should always 0 that it never track
        });

        // Define filtered output value
        Cell<String> fId = id.lift(isValid, (l, r) -> r ? l : "");
        Cell<String> fLat = lat.lift(isValid, (l, r) -> r ? String.valueOf(l) : "");
        Cell<String> fLon = lon.lift(isValid, (l, r) -> r ? String.valueOf(l) : "");
        Cell<String> fTime = time.lift(isValid, (l, r) -> r ? formatTime(l) : "");
        Cell<String> fDist = dist.lift(isValid, (l, r) -> r ? String.valueOf(l) : "");

        filterResults.add(fId);
        filterResults.add(fLat);
        filterResults.add(fLon);
        filterResults.add(fTime);
        filterResults.add(fDist);

        return filterResults;
    }

    public static void main(String[] args) {
        // Initialize the GPS Service
        GpsService gpsService = new GpsService();

        // Retrieve the event streams from GpsService
        Stream<GpsEvent>[] gpsStreams = gpsService.getEventStreams();

        // Display the GUI
        GpsGUI gui = new GpsGUI(gpsStreams);
        gui.show();
    }

    // Retrieve the calculated distances for each tracker from the filtered events
    public static Map<String, Double> getTotalDistancesRecord() {
        return totalDistancesRecord;
    }

    // The method only can be used for testing purposes
    public static void clearTotalDistancesRecord() {
        totalDistancesRecord.clear();
    }

    // GUI initialization: combine all panels to the window
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

    /* Single Display (1) GUI -- Part I Ten simplifier tracker  */
    public JPanel SimplifyDisplayPanel(String title) {
        List<List<Cell<String>>> simplyInfo = simplifiedTrackers(gpsEvents);

        return SimplifyDisplayGUI(title, simplyInfo);
    }

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

    /* Single Display (1) GUI -- Part II Current Event coming in */
    public JPanel CurrentTrackerPanel(String title) {
        JPanel panel = CurrTrackerGUI(title);

        Transaction.runVoid(() -> {
            // Step 2: Set up the FRP logic and get the content cell
            Cell<String> content = currentTracker(gpsEvents);

            // Step 3: Bind the content cell to an SLabel and add it to the panel
            SLabel currentEventTexts = new SLabel(content);
            panel.add(currentEventTexts);
        });

        return panel;
    }

    private JPanel CurrTrackerGUI(String title) {
        JPanel panel = new JPanel(new GridLayout(1, 1, 5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    /* Single Display (2) GUI */
    public JPanel FilteredTrackerDisplayPanel(String title) {
        JSplitPane controlPanel = ControlPanel();

        JPanel fEventDisplayPanel = FilterEvDisplayPanel(title);

        JPanel mainPanel = new JPanel(new BorderLayout());

        // Create JSplitPane with FilterEvDisplayPanel and ControlPanel
        JSplitPane vertSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, fEventDisplayPanel, controlPanel);
        vertSplitPane.setResizeWeight(0.8);

        // Add the split pane to the main panel
        mainPanel.add(vertSplitPane, BorderLayout.CENTER);

        return mainPanel;
    }

    /* Single Display (2) GUI -- Part I user input textFiles, result label and set button */
    private JSplitPane ControlPanel() {
        // Create ControlPanel
        JSplitPane controlPanel;

        // Left sub-ControlPanel for text fields with labels and setting button
        JPanel leftPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcLeft = new GridBagConstraints();
        gbcLeft.insets = new Insets(10, 10, 10, 10);
        gbcLeft.fill = GridBagConstraints.HORIZONTAL;

        // Input text labels
        List<JLabel> fieldLabels = new ArrayList<>();
        fieldLabels.add(new JLabel("LatitudeMax(-90, 90)"));
        fieldLabels.add(new JLabel("LatitudeMin(-90, 90)"));
        fieldLabels.add(new JLabel("LongitudeMax(-180, 180)"));
        fieldLabels.add(new JLabel("LongitudeMin(-180, 180)"));

        // Input text fields
        List<STextField> textFields = new ArrayList<>();
        // Use the elements frequently, so retain the variables
        STextField latMax = new STextField("", 15);
        STextField latMin = new STextField("", 15);
        STextField lonMax = new STextField("", 15);
        STextField lonMin = new STextField("", 15);
        textFields.add(latMax);
        textFields.add(latMin);
        textFields.add(lonMax);
        textFields.add(lonMin);
        // add textFields and labels to sub-leftPanel
        for (int i = 0; i < textFields.size(); i++) {
            // Add label on the left side of the text field
            gbcLeft.gridx = 0;
            gbcLeft.gridy = i;
            gbcLeft.anchor = GridBagConstraints.EAST;
            leftPanel.add(fieldLabels.get(i), gbcLeft);

            // Add text field
            gbcLeft.gridx = 1;
            gbcLeft.anchor = GridBagConstraints.WEST;
            leftPanel.add(textFields.get(i), gbcLeft);
        }

        // Store user input values
        rangeVals.add(convertInputs(textFields.get(0), -90, 90));
        rangeVals.add(convertInputs(textFields.get(1), -90, 90));
        rangeVals.add(convertInputs(textFields.get(2), -180, 180));
        rangeVals.add(convertInputs(textFields.get(3), -180, 180));

        // Ensure all inputs value are valid
        Cell<Boolean> allValid = new Cell<>(true);
        for (Cell<Optional<Double>> rangeVal : rangeVals) {
            Cell<Boolean> noEmptyValid = rangeVal.map(Optional::isPresent); // ensure no empty
            allValid = allValid.lift(noEmptyValid, (a, b) -> a && b);
        }
        Cell<Boolean> minMaxValid = rangeVals.get(0).lift(rangeVals.get(1), rangeVals.get(2), rangeVals.get(3), (a, b, c, d) ->
                a.isPresent() && b.isPresent() && c.isPresent() && d.isPresent() &&
                        a.get() > b.get() && c.get() > d.get()); // Ensure max value greater min
        Cell<Boolean> rangeValid = rangeVals.get(0).lift(rangeVals.get(1), rangeVals.get(2), rangeVals.get(3), (a, b, c, d) ->
                a.isPresent() && b.isPresent() && c.isPresent() && d.isPresent() &&
                        a.get() >= -90 && b.get() <= 90 && c.get() >= -180 && d.get() <= 180);

        allValid = allValid.lift(minMaxValid, rangeValid, (a, b, c) -> a && b && c); // combine all conditions together

        // Setting Button
        setButton = new SButton("Set", allValid); // only all input valid can click the button
        gbcLeft.gridx = 0;
        gbcLeft.gridy = textFields.size();
        gbcLeft.gridwidth = 2;
        gbcLeft.anchor = GridBagConstraints.SOUTH;
        leftPanel.add(setButton, gbcLeft);

        // Right sub-ControlPanel for result label
        JPanel rightPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcRight = new GridBagConstraints();
        gbcRight.insets = new Insets(5, 5, 5, 5);
        gbcRight.fill = GridBagConstraints.HORIZONTAL;

        // Add result label
        Stream<String> storeResult = setButton.sClicked
                .snapshot(latMin.text.lift(latMax.text, lonMin.text, lonMax.text, (a, b, c, d)
                        -> String.format("Latitude(" + a + ", " + b + ")" + " Longitude(" + c + ", " + d + ")")));
        Cell<String> result = storeResult.hold("Input must: numeric(include -); max > min");
        SLabel resultLabel = new SLabel(result);
        gbcRight.gridx = 0;
        gbcRight.gridy = 1;
        gbcRight.anchor = GridBagConstraints.CENTER;
        rightPanel.add(resultLabel, gbcRight);

        // Combine left-sub and right-sub to control panel (include input with labels, button and result)
        controlPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        controlPanel.setDividerLocation(500);
        controlPanel.setResizeWeight(0.5);
        controlPanel.setBorder(BorderFactory.createTitledBorder("Restrict Dimension"));

        return controlPanel;
    }

    /* Single Display (2) GUI -- Part II show filtered trackers' info: id, lat, lon, time, distance (each 5-min update) */
    private JPanel FilterEvDisplayPanel(String title) {
        // Create FilterEvDisplayPanel
        JPanel displayPanel = FilterEvDisplayGUI(title);

        // Dynamically output result on GUI
        for (Stream<GpsEvent> gpsEvent : gpsEvents) {
            // provide the time interval when calculate distance
            long windowSizeMillis = 30000; // It could be modified when testing

            /* Core event-drive logic */
            List<Cell<String>> results = filteredEvents(rangeVals, setButton, windowSizeMillis, gpsEvent);

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
