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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

            // continuously emits the system time every second. This is an out-of-FRP method
            StreamSink<Long> sysTimeStream = new StreamSink<>();
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(() -> {
                long currentTime = System.currentTimeMillis();
                sysTimeStream.send(currentTime); // Push current system time into the stream
            }, 0, 1, TimeUnit.SECONDS);

            // Hold the latest system time in a cell
            Cell<Long> sysTimeCell = sysTimeStream.hold(System.currentTimeMillis());

            // merge all coming in events as last event
            Stream<GpsEvent> lastGpsStream = gpsEvents[0];
            for (int i = 1; i < gpsEvents.length; i++) {
                lastGpsStream = lastGpsStream.orElse(gpsEvents[i]);
            }

            CellLoop<GpsData> prevData = new CellLoop<>();

            Stream<GpsData> sWrapTime = lastGpsStream.snapshot(timer, (ev, t)
                    -> new GpsData(ev.name, String.valueOf(ev.latitude), String.valueOf(ev.longitude), t));
            Stream<GpsData> sSetNew = sWrapTime.snapshot(prevData, (ev, c) -> !ev.equals(c) ? ev : null);
            prevData.loop(sSetNew.filter(Objects::nonNull).hold(new GpsData("", "", "", 0L)));

            Cell<String> clean = new Cell<>("");
            Cell<String> content = clean.lift(sysTimeCell, prevData, (empty, sysTime, cont)
                    -> (sysTime - cont.time > 3000) ? empty : cont).map(Object::toString);

            SLabel currentEventTexts = new SLabel(content);

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
        /* Set GUI **/
        JPanel panel = new JPanel(new BorderLayout());

        // Create textPanel
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        List<JPanel> textSubPanels = new ArrayList<>();

        // Create controlPanel
        JSplitPane controlPanel;
        // Left panel for text fields and labels
        JPanel leftPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcLeft = new GridBagConstraints();
        gbcLeft.insets = new Insets(10, 10, 10, 10);
        gbcLeft.fill = GridBagConstraints.HORIZONTAL;

        List<JLabel> fieldLabels = new ArrayList<>();
        fieldLabels.add(new JLabel("LatitudeMax(-90, 90)"));
        fieldLabels.add(new JLabel("LatitudeMin(-90, 90)"));
        fieldLabels.add(new JLabel("LongitudeMax(-180, 180)"));
        fieldLabels.add(new JLabel("LatitudeMin(-180, 180)"));

        // Use the elements frequently, so retain the variables
        STextField latMax = new STextField("", 15);
        STextField latMin = new STextField("", 15);
        STextField lonMax = new STextField("", 15);
        STextField lonMin = new STextField("", 15);

        List<STextField> textFields = new ArrayList<>();
        textFields.add(latMax);
        textFields.add(latMin);
        textFields.add(lonMax);
        textFields.add(lonMin);

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

        // Add button at the bottom of the left panel
        SButton setButton = new SButton("Set");
        gbcLeft.gridx = 0;
        gbcLeft.gridy = textFields.size();
        gbcLeft.gridwidth = 2;
        gbcLeft.anchor = GridBagConstraints.SOUTH;
        leftPanel.add(setButton, gbcLeft);

        // Right panel for button and result label
        JPanel rightPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcRight = new GridBagConstraints();
        gbcRight.insets = new Insets(5, 5, 5, 5);
        gbcRight.fill = GridBagConstraints.HORIZONTAL;

        // Add result label
        /* Core part: validate input fields */
        Stream<String> storeResult = setButton.sClicked
                .snapshot(latMin.text.lift(latMax.text, lonMin.text, lonMax.text, (a, b, c, d)
                        -> String.format("Latitude(" + a + ", " + b + ")" + " Longitude(" + c + ", " + d + ")")));

        // TODO: This logic duplicate with core-event-drive logic part, but this logic currently used for print string not directly use double value; consider how to combine them finally
        List<Cell<Optional<Double>>> rangeVals = new ArrayList<>();
        rangeVals.add(convertValue(textFields.get(0), -90, 90));
        rangeVals.add(convertValue(textFields.get(1), -90, 90));
        rangeVals.add(convertValue(textFields.get(2), -180, 180));
        rangeVals.add(convertValue(textFields.get(3), -180, 180));

        // Ensure all inputs value are valid
        Cell<Boolean> allValid = new Cell<>(true);

        for (Cell<Optional<Double>> rangeVal : rangeVals) {
            Cell<Boolean> noEmptyValid = rangeVal.map(Optional::isPresent); // ensure no empty
            allValid = allValid.lift(noEmptyValid, (a, b) -> a && b);
        }
        Cell<Boolean> minMaxValid = rangeVals.get(0).lift(rangeVals.get(1), rangeVals.get(2), rangeVals.get(3), (a, b, c, d) -> {
            if (a.isPresent() && b.isPresent() && c.isPresent() && d.isPresent()) {
                return a.get() > b.get() && c.get() > d.get(); // ensure max value greater min value
            }
            return false;
        });
        allValid = allValid.lift(minMaxValid, (a, b) -> a && b); // combine all conditions together

        // Filter: only all conditions true can propagate the event
        Stream<String> showResult = storeResult.snapshot(allValid, (text, cond) -> cond ? text : "Input must Numeric; All Max must greater Min");

        // Adjust result label panel
        Cell<String> result = showResult.hold("");
        SLabel resultLabel = new SLabel(result); // Display user input once button clicked
        gbcRight.gridx = 0;
        gbcRight.gridy = 1;
        gbcRight.anchor = GridBagConstraints.CENTER;
        rightPanel.add(resultLabel, gbcRight);

        controlPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        controlPanel.setDividerLocation(500);
        controlPanel.setResizeWeight(0.5);
        controlPanel.setBorder(BorderFactory.createTitledBorder("Restrict Dimension"));

        /* Core Event-Drive Logic Begin **/
        TimerSystem<Long> timerSystem = new MillisecondsTimerSystem();
        Cell<Long> timer = timerSystem.time;

        // Only update the restriction when click button
        Cell<Optional<Double>> latMaxAfterClick = setButton.sClicked.snapshot(rangeVals.get(0), (u, r) -> r).hold(Optional.empty());
        Cell<Optional<Double>> latMinAfterClick = setButton.sClicked.snapshot(rangeVals.get(1), (u, r) -> r).hold(Optional.empty());
        Cell<Optional<Double>> lonMaxAfterClick = setButton.sClicked.snapshot(rangeVals.get(2), (u, r) -> r).hold(Optional.empty());
        Cell<Optional<Double>> lonMinAfterClick = setButton.sClicked.snapshot(rangeVals.get(3), (u, r) -> r).hold(Optional.empty());

        // TODO: Currently only can receive one event fit restriction; need accept all in future; also need consider how to refine dynamic panel addition
        Stream<GpsEvent> filteredEvents = gpsEvents[0];
        for (int i = 1; i < gpsEvents.length; i++) {
            // Filter events
            Cell<Double> lat = gpsEvents[i].map(ev -> ev.latitude).hold(0.0);
            Cell<Double> lon = gpsEvents[i].map(ev -> ev.longitude).hold(0.0);
            Cell<Boolean> isValid = new Cell<>(true);
            Cell<Boolean> latValid = lat.lift(latMaxAfterClick, latMinAfterClick, (evLat, max, min) -> {
                if (max.isPresent() && min.isPresent()) {
                    System.out.println("Current GPS Lat " + evLat);
                    System.out.println("Current Max Lat " + max.get());
                    System.out.println("Current Min Lat " + min.get());
                    return evLat < max.get() && evLat > min.get();
                }
                return false;
            });
            Cell<Boolean> lonValid = lon.lift(lonMaxAfterClick, lonMinAfterClick, (evLon, max, min) -> {
                if (max.isPresent() && min.isPresent()) {
                    System.out.println("Current GPS Lon " + evLon);
                    System.out.println("Current Max Lon " + max.get());
                    System.out.println("Current Min Lon " + min.get());
                    return evLon < max.get() && evLon > min.get();
                }
                return false;
            });
            isValid = isValid.lift(latValid, lonValid, (a, b, c) -> {
                System.out.println();
                System.out.println("Current latValid status " + b);
                System.out.println("Current lonValid status " + c);
                System.out.println();
                return a && b && c;
            });
            filteredEvents = filteredEvents.orElse(gpsEvents[i]).snapshot(isValid, (ev, val) -> val ? ev : null).filter(Objects::nonNull);
        }

        JPanel currentTextPanel = new JPanel();
        currentTextPanel.setLayout(new BoxLayout(currentTextPanel, BoxLayout.X_AXIS));
        // Start Showing events
        Cell<String> trackerId = filteredEvents.map(ev -> ev.name).hold("No Tracker Now");
        Cell<String> latitude = filteredEvents.map(ev -> "Latitude" + ev.latitude).hold("");
        Cell<String> longitude = filteredEvents.map(ev -> "Longitude" + ev.longitude).hold("");
        Cell<String> altitude = filteredEvents.map(ev -> "Distance" + ev.altitude).hold("");
        Cell<String> timeStamp = filteredEvents.snapshot(timer).map(t -> "Time" + formatTime(t)).hold("");
        SLabel ids = new SLabel(trackerId);
        SLabel lats = new SLabel(latitude);
        SLabel lons = new SLabel(longitude);
        SLabel times = new SLabel(timeStamp);
        SLabel dist = new SLabel(altitude); // TODO: need change to real calculation
        currentTextPanel.add(ids);
        currentTextPanel.add(Box.createRigidArea(new Dimension(50, 0)));
        currentTextPanel.add(lats);
        currentTextPanel.add(Box.createRigidArea(new Dimension(50, 0)));
        currentTextPanel.add(lons);
        currentTextPanel.add(Box.createRigidArea(new Dimension(50, 0)));
        currentTextPanel.add(times);
        currentTextPanel.add(Box.createRigidArea(new Dimension(50, 0)));
        currentTextPanel.add(dist);
        currentTextPanel.add(Box.createRigidArea(new Dimension(50, 0)));
        textSubPanels.add(currentTextPanel);

        // Finalise GUI addition
        for (JPanel subPanel : textSubPanels) {
            System.out.println("We have " + textSubPanels.size() + " sub panels now");
            textPanel.add(subPanel);
        }
        // Create JSplitPane with textPanel and setPanel
        JSplitPane vertSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, textPanel, controlPanel);
        vertSplitPane.setResizeWeight(0.8);
        // Add the split pane to the main panel
        panel.add(vertSplitPane, BorderLayout.CENTER);
        panel.setBorder(BorderFactory.createTitledBorder(title));

        return panel;
    }

    private Cell<Optional<Double>> convertValue(STextField textField, double min, double max) {
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

    public String toString() {

        return trackerID + ", Latitude " + latitude + ", Longitude " + longitude + ", Time: " + formatTime(time);
    }

    public boolean equals(GpsData other) {
        return this.trackerID.equals(other.trackerID) &&
                this.latitude.equals(other.latitude) &&
                this.longitude.equals(other.longitude);
    }

    private String formatTime(long time) {
        DateTimeFormatter TIME_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        Instant instant = Instant.ofEpochMilli(time);
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return localDateTime.format(TIME_FORMATTER);
    }
}
