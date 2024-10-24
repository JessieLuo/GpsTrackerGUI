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
        JPanel textPanel = new JPanel(new GridLayout(gpsEvents.length + 1, 1, 5, 5));
        textPanel.add(new JLabel("ID"));
        textPanel.add(new JLabel("Latitude"));
        textPanel.add(new JLabel("Longitude"));
        textPanel.add(new JLabel("Time"));
        textPanel.add(new JLabel("Distance"));

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

        Stream<String> storeResult = setButton.sClicked
                .snapshot(latMin.text.lift(latMax.text, lonMin.text, lonMax.text, (a, b, c, d)
                        -> String.format("Latitude(" + a + ", " + b + ")" + " Longitude(" + c + ", " + d + ")")));
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

        // Create JSplitPane with textPanel and setPanel
        JSplitPane vertSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, textPanel, controlPanel);
        vertSplitPane.setResizeWeight(0.8);

        // Add the split pane to the main panel
        panel.add(vertSplitPane, BorderLayout.CENTER);
        panel.setBorder(BorderFactory.createTitledBorder(title));

        /* Core Event-Drive Logic Begin **/
        TimerSystem<Long> timerSystem = new MillisecondsTimerSystem();
        Cell<Long> timer = timerSystem.time;

        // TODO: use filtered gpsEvents in future
        Stream<GpsEvent>[] filteredGpsEvents;
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
            // need change
            SLabel dist = new SLabel(altitude);

            textPanel.add(id);
            textPanel.add(lat);
            textPanel.add(lon);
            textPanel.add(time);
            textPanel.add(dist);
        }

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
