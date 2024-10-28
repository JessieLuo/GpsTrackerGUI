import nz.sodium.*;
import nz.sodium.time.MillisecondsTimerSystem;
import nz.sodium.time.TimerSystem;
import swidgets.SButton;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * EventProcessor is a utility class for handling and processing GPS event data with Sodium FRP primitives.
 * It is designed to support a GPS tracking GUI application by filtering, recording, and calculating distance
 * data for multiple trackers in real time. <br><br>
 * <p>
 * This class provides essential functionalities to meet the following requirements:
 * <ul>
 *   <li><b>Simplified Tracker Information:</b> Processes incoming GPS streams for multiple trackers, removing
 *       altitude data to provide only tracker ID, latitude, and longitude. This simplified information is displayed
 *       in individual cells for each tracker.</li>
 *   <li><b>Current Event Tracking:</b> Maintains and displays a single, most recent GPS event for each tracker
 *       as a comma-delimited string (ID, latitude, longitude, time) when received. This display clears automatically
 *       after 3 seconds of inactivity.</li>
 *   <li><b>GPS Event Filtering:</b> Filters GPS events based on user-defined latitude and longitude range inputs,
 *       managed through an interactive control panel. This uses the Sodium FRP <code>snapshot</code> primitive to
 *       capture events within the specified geographic range and dynamically displays data in a format identical
 *       to the current event display.</li>
 *   <li><b>Distance Calculation:</b> Tracks and calculates the total distance traveled by each tracker over a
 *       5-minute sliding time window. The calculation includes only GPS events that fall within the active latitude
 *       and longitude range. Altitude is converted from feet to meters for accurate 3D distance measurement.
 *       Distance is rounded to the nearest integer and displayed in meters.</li>
 * </ul>
 * <p>
 * The EventProcessor class maintains various tracker-specific records, such as position, cumulative distance,
 * and elapsed time since the last recorded event. These records support the dynamic and accurate representation
 * of GPS tracking data over time.
 */
public class EventProcessor {
    // Record events for specific tracker
    private static final Map<String, Position> positionsRecord = new HashMap<>(); // separate each tracker with it current position info
    private static final Map<String, Double> totalDistancesRecord = new HashMap<>(); // store each tracker travelled distance
    private static final Map<String, Long> totalTimeRecord = new HashMap<>(); // record current event fired time
    /* actually used for distance calculation. recorded only when the time interval is reached */
    private static final Map<String, Double> timeBasedTotalDistRecord = new HashMap<>();
    private static final double FEET_TO_METER = 0.3048; // convert altitude from feet to meter

    /**
     * Processes simplified tracking information by excluding altitude data, returning only the
     * essential tracker information for display purposes.
     *
     * @param gpsEvents Array of streams, each representing a continuous flow of GPS events for individual trackers.
     * @return A list containing tracker details where each list entry corresponds to a cell of information:
     * list of IDs, list of latitudes, and list of longitudes.
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
     * Processes and maintains the latest GPS event data for display, refreshing as new events arrive.
     * The current event data is cleared automatically if no new events arrive within a 3-second window.
     *
     * @param gpsEvents Array of streams, each representing continuous GPS event data for individual trackers.
     * @return A cell containing the latest event information as a formatted string "[id, lat, lon, time]".
     * If no events occur within the 3-second interval, the cell returns an empty string.
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
     * Filters GPS events based on user-defined latitude and longitude restrictions. When an event falls within the defined
     * range, the event details are processed and displayed. If an event does not meet the conditions, all displayed values
     * for that event are empty. Distance is calculated for each tracker within the specified time window only for
     * events that meet the restrictions.
     *
     * <p>The latitude and longitude range can be set through user input fields for maximum and minimum values, and
     * clicking the `setButton` applies these restrictions. Only events within the specified range are displayed, and
     * the cumulative distance for each tracker is updated accordingly.</p>
     *
     * @param inputVals        List of user-defined latitude and longitude range values: maximum and minimum latitude,
     *                         maximum and minimum longitude.
     * @param setButton        Button to apply the restriction range values defined in `inputVals`.
     * @param windowSizeMillis Time interval (in milliseconds) used to calculate cumulative distance.
     * @param gpsEvent         Current GPS event data stream.
     * @return List of `Cell<String>` containing event information, where each entry corresponds to [id, lat, lon, time, dist].
     * If an event does not meet the conditions, the entries are empty strings.
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
                    distance = Utils.calculateDistance(lastPosition, currentPosition);

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
        Cell<String> fTime = time.lift(isValid, (l, r) -> r ? Utils.formatTime(l) : "");
        Cell<String> fDist = dist.lift(isValid, (l, r) -> r ? String.valueOf(l) : "");

        filterResults.add(fId);
        filterResults.add(fLat);
        filterResults.add(fLon);
        filterResults.add(fTime);
        filterResults.add(fDist);

        return filterResults;
    }

    // The method only used for test purpose
    public static Map<String, Double> getTotalDistancesRecord() {
        return totalDistancesRecord;
    }
}
