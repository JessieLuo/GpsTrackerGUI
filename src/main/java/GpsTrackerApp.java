import nz.sodium.Stream;

public class GpsTrackerApp {

    public static void main(String[] args) {
        // Initialize the GPS Service
        GpsService gpsService = new GpsService();

        // Retrieve the event streams from GpsService
        Stream<GpsEvent>[] gpsStreams = gpsService.getEventStreams();

        // Start the GUI and pass the streams to it
        new GpsTrackerGUI(gpsStreams);
    }
}
