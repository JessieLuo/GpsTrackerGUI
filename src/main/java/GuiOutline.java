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

public class GuiOutline {
    private final JFrame frame = new JFrame("GPS Tracking Application");
    private final Stream<GpsEvent>[] gpsEvents;
    // provide the time interval when calculate distance
    private final long windowSizeMillis = 30000; // It could be modified when testing

    public GuiOutline(Stream<GpsEvent>[] gpsEvents) {
        this.gpsEvents = gpsEvents;
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

    // Safely convert input string to double value
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

    // GUI initialization: combine all panels to the window
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

        // Place the main panels side by side
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Add the left component
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.4;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(allTrackerSplitPane, gbc);

        // Add the right component
        gbc.gridx = 1;
        gbc.weightx = 0.6;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(filteredTrackerDisplayPanel, gbc);

        frame.setLayout(new BorderLayout());
        frame.add(mainPanel, BorderLayout.CENTER);
    }

    /* Single Display (1) -- Part I Ten simplifier tracker  */
    public JPanel TrackerDisplayPanel(String title, Stream<GpsEvent>[] gpsEvents) {
        int columnCount = 3; // Columns: Tracker ID, Lat, Lon
        int rowCount = gpsEvents.length;
        JPanel panel = new JPanel(new GridLayout(rowCount + 1, columnCount, 5, 5)); // +1 for the header row
        panel.setBorder(BorderFactory.createTitledBorder(title));

        panel.add(new JLabel("ID"));
        panel.add(new JLabel("Latitude"));
        panel.add(new JLabel("Longitude"));

        // show each stream for each tracker
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

    /* Single Display (1) -- Part II Current Event coming in */
    public JPanel CurrentTrackerPanel(String title, Stream<GpsEvent>[] gpsEvents) {
        JPanel panel = new JPanel(new GridLayout(1, 1, 5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(title));

        Transaction.runVoid(() -> {
            // Capture system time
            TimerSystem<Long> timerSystem = new MillisecondsTimerSystem();
            Cell<Long> timer = timerSystem.time;
            // continuously emits the system time every second.
            StreamSink<Long> sysTimeStream = new StreamSink<>();
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(() -> {
                long currentTime = System.currentTimeMillis();
                sysTimeStream.send(currentTime); // Push current system time into the stream
            }, 0, 1, TimeUnit.SECONDS);
            // Hold the latest system time in a cell
            Cell<Long> sysTimeValue = sysTimeStream.hold(System.currentTimeMillis());

            /* merge all coming in events as current event to show on GUI */
            Stream<GpsEvent> lastGpsStream = gpsEvents[0];
            for (int i = 1; i < gpsEvents.length; i++) {
                lastGpsStream = lastGpsStream.orElse(gpsEvents[i]);
            }

            // Record data from current event
            CellLoop<GpsData> currData = new CellLoop<>();

            Stream<GpsData> sWrapTime = lastGpsStream.snapshot(timer, (ev, t)
                    -> new GpsData(ev.name, String.valueOf(ev.latitude), String.valueOf(ev.longitude), t));
            Stream<GpsData> sSetNew = sWrapTime.snapshot(currData, (ev, c) -> !ev.equals(c) ? ev : null);

            currData.loop(sSetNew.filter(Objects::nonNull).hold(new GpsData("", "", "", 0L)));

            /* Clean event if its data not update over 3 seconds */
            Cell<String> content = sysTimeValue.lift(currData, (sysTime, data)
                    -> (sysTime - data.time > 3000) ? "" : data).map(Object::toString);

            SLabel currentEventTexts = new SLabel(content);

            panel.add(currentEventTexts);
        });

        return panel;
    }

    /* Single Display (2) -- Include setting button, input textFields and filtered tracker displays */
    public JPanel FilteredTrackerDisplayPanel(String title, Stream<GpsEvent>[] gpsEvents) {
        /* Setting GUI **/
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Create inputPanel
        JPanel inputPanel = new JPanel(new GridLayout(gpsEvents.length + 1, 5, 5, 5));
        inputPanel.setBorder(BorderFactory.createTitledBorder(title));
        inputPanel.add(new JLabel("ID"));
        inputPanel.add(new JLabel("Latitude"));
        inputPanel.add(new JLabel("Longitude"));
        inputPanel.add(new JLabel("Time"));
        inputPanel.add(new JLabel("Distance"));

        // Create controlPanel
        JSplitPane controlPanel;

        // Left sub-controlPanel for text fields with labels and setting button
        JPanel leftPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcLeft = new GridBagConstraints();
        gbcLeft.insets = new Insets(10, 10, 10, 10);
        gbcLeft.fill = GridBagConstraints.HORIZONTAL;

        // Input text labels
        List<JLabel> fieldLabels = new ArrayList<>();
        fieldLabels.add(new JLabel("LatitudeMax(-90, 90)"));
        fieldLabels.add(new JLabel("LatitudeMin(-90, 90)"));
        fieldLabels.add(new JLabel("LongitudeMax(-180, 180)"));
        fieldLabels.add(new JLabel("LatitudeMin(-180, 180)"));

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

        // Prepare setting button click-available condition
        List<Cell<Optional<Double>>> rangeVals = new ArrayList<>();
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
        SButton setButton = new SButton("Set", allValid); // only all input valid can click the button
        gbcLeft.gridx = 0;
        gbcLeft.gridy = textFields.size();
        gbcLeft.gridwidth = 2;
        gbcLeft.anchor = GridBagConstraints.SOUTH;
        leftPanel.add(setButton, gbcLeft);

        // Right sub-controlPanel for result label
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

        /* Core Event-Drive Logic Begin **/
        // Provide a cell to acquire time when event fired
        Transaction.runVoid(() -> {
            TimerSystem<Long> timerSystem = new MillisecondsTimerSystem();
            Cell<Long> timer = timerSystem.time;
            // Only update the restriction when click button
            Cell<Optional<Double>> latMaxAfterClick = setButton.sClicked
                    .snapshot(rangeVals.get(0), (u, r) -> r).hold(Optional.empty());
            Cell<Optional<Double>> latMinAfterClick = setButton.sClicked
                    .snapshot(rangeVals.get(1), (u, r) -> r).hold(Optional.empty());
            Cell<Optional<Double>> lonMaxAfterClick = setButton.sClicked
                    .snapshot(rangeVals.get(2), (u, r) -> r).hold(Optional.empty());
            Cell<Optional<Double>> lonMinAfterClick = setButton.sClicked
                    .snapshot(rangeVals.get(3), (u, r) -> r).hold(Optional.empty());

            // Record events for specific tracker
            Map<String, Position> positionsRecord = new HashMap<>(); // separate each tracker with it current position info
            Map<String, Double> totalDistancesRecord = new HashMap<>(); // store each tracker travelled distance
            Map<String, Long> totalTimeRecord = new HashMap<>(); // record current event fired time
            Map<String, Double> timeBasedTotalDistRecord = new HashMap<>(); // record the distance only events cumulative pass time equal to intervals

            for (Stream<GpsEvent> gpsEvent : gpsEvents) {
                // Extract value
                Cell<String> id = gpsEvent.map(ev -> ev.name).hold("");
                Cell<Double> lat = gpsEvent.map(ev -> ev.latitude).hold(0.0);
                Cell<Double> lon = gpsEvent.map(ev -> ev.longitude).hold(0.0);
                Cell<Double> alt = gpsEvent.map(ev -> ev.altitude * 0.3048).hold(0.0); // convert feet to meter
                Cell<Long> time = gpsEvent.snapshot(timer).hold(0L); // event occurs time

                // Start filtering
                Cell<Boolean> isValid = new Cell<>(true);
                Cell<Boolean> latValid = lat.lift(latMaxAfterClick, latMinAfterClick, (evLat, max, min)
                        -> max.isPresent() && min.isPresent() && evLat < max.get() && evLat > min.get());
                Cell<Boolean> lonValid = lon.lift(lonMaxAfterClick, lonMinAfterClick, (evLon, max, min)
                        -> max.isPresent() && min.isPresent() && evLon < max.get() && evLon > min.get());
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
                    return 0.0;
                });

                // Define output value
                Cell<String> fId = id.lift(isValid, (l, r) -> r ? l : "");
                Cell<String> fLat = lat.lift(isValid, (l, r) -> r ? String.valueOf(l) : "");
                Cell<String> fLon = lon.lift(isValid, (l, r) -> r ? String.valueOf(l) : "");
                Cell<String> fTime = time.lift(isValid, (l, r) -> r ? formatTime(l) : "");
                Cell<String> fDist = dist.lift(isValid, (l, r) -> r ? String.valueOf(l) : ""); // TODO: notice ensure put dist in

                // Add GUI elements to correspond panel
                SLabel filterId = new SLabel(fId);
                SLabel filterLat = new SLabel(fLat);
                SLabel filterLon = new SLabel(fLon);
                SLabel filterTime = new SLabel(fTime);
                SLabel filterDist = new SLabel(fDist);

                inputPanel.add(filterId);
                inputPanel.add(filterLat);
                inputPanel.add(filterLon);
                inputPanel.add(filterTime);
                inputPanel.add(filterDist);
            }
        });

        // Create JSplitPane with inputPanel and controlPanel
        JSplitPane vertSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, inputPanel, controlPanel);
        vertSplitPane.setResizeWeight(0.8);
        // Add the split pane to the main panel
        mainPanel.add(vertSplitPane, BorderLayout.CENTER);

        return mainPanel;
    }

    public void show() {
        frame.setVisible(true);
    }

    public boolean isFrameVisible() {
        return frame.isVisible();
    }
}
