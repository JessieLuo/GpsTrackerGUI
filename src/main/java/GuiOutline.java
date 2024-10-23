import nz.sodium.Cell;
import nz.sodium.CellLoop;
import nz.sodium.Stream;
import nz.sodium.Transaction;
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

            // merge all coming in events as last event
            Stream<GpsEvent> lastGpsStream = gpsEvents[0];
            for (int i = 1; i < gpsEvents.length; i++) {
                lastGpsStream = lastGpsStream.orElse(gpsEvents[i]);
            }

            // Get the event occurs time
            Cell<String> formattedCurrTime = lastGpsStream
                    .snapshot(timer).hold(0L)
                    .map(timeInMillions -> {
                        System.out.println("Current event occurs time " + formatTime(timeInMillions));
                        return ", Time: " + formatTime(timeInMillions);
                    });

            // Basic content in current display panel
            Cell<String> content = lastGpsStream.map(ev -> ev.name + ", Latitude:" + ev.latitude + ", Longitude: " + ev.longitude).hold("").lift(formattedCurrTime, (l, r) -> l + r);

            // clean content when it outdated
            Cell<String> clean = new Cell<>("");

            // capture changes
            Stream<GpsData> sCurrentGpsData = lastGpsStream.map(ev -> new GpsData(ev.name, String.valueOf(ev.latitude), String.valueOf(ev.longitude), System.currentTimeMillis()));

            // track the previous state
            CellLoop<GpsData> previousGpsData = new CellLoop<>();

            // detect changes in the GPS data
            Stream<GpsData> sChanged = sCurrentGpsData.snapshot(previousGpsData, (curr, prev) -> {
                System.out.println("Occurred Event Time: " + formatTime(prev.time));
                return !curr.equals(prev) ? curr : null;
            });

            // Only update when data change
            Stream<GpsData> sUpdate = sChanged.filter(Objects::nonNull);

            // update the previous data when it changed
            previousGpsData.loop(sUpdate.hold(new GpsData("", "", "", 0L)));

            // check if content has remained unchanged for 3 seconds
            Stream<Long> sLastChangeTime = sUpdate.snapshot(timer, (changed, currentTime) -> currentTime);

            // hold the last change time
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
        /* Set GUI **/
        JPanel panel = new JPanel(new BorderLayout());

        // Create textPanel with labels
        JPanel textPanel = new JPanel(new GridLayout(gpsEvents.length + 1, 1, 5, 5));
        textPanel.add(new JLabel("ID"));
        textPanel.add(new JLabel("Latitude"));
        textPanel.add(new JLabel("Longitude"));
        textPanel.add(new JLabel("Time"));
        textPanel.add(new JLabel("Distance"));

        // Create setPanel with GridBagLayout and button
        JPanel setPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.CENTER;
        SButton setButton = new SButton("Set");
        setPanel.add(setButton, gbc);

        // Create JSplitPane with textPanel and setPanel
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, textPanel, setPanel);
        splitPane.setResizeWeight(0.8);

        // Add the split pane to the main panel
        panel.add(splitPane, BorderLayout.CENTER);
        panel.setBorder(BorderFactory.createTitledBorder(title));

        /* Core Logic Begin **/
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
