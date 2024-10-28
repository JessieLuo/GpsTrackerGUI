import nz.sodium.*;
import nz.sodium.time.MillisecondsTimerSystem;
import nz.sodium.time.TimerSystem;
import swidgets.SButton;

import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EventProcessor {
    // Record events for specific tracker
    private static final Map<String, Position> positionsRecord = new HashMap<>(); // separate each tracker with it current position info
    private static final Map<String, Double> totalDistancesRecord = new HashMap<>(); // store each tracker travelled distance
    private static final Map<String, Long> totalTimeRecord = new HashMap<>(); // record current event fired time
    /* actually used for distance calculation. recorded only when the time interval is reached */
    private static final Map<String, Double> timeBasedTotalDistRecord = new HashMap<>();
    private static final double FEET_TO_METER = 0.3048; // convert altitude from feet to meter

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
