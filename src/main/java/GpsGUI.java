import nz.sodium.Stream;

/**
 *
 */
public class GpsGUI {
    public GpsGUI(Stream<GpsEvent>[] gpsStreams) {
    }

    public static void main(String[] args) {
        GuiOutline gui = new GuiOutline();
        gui.show();
        // Initialize the GPS Service
        GpsService gpsService = new GpsService();

        // Retrieve the event streams from GpsService
//        Stream<GpsEvent>[] gpsStreams = gpsService.getEventStreams();

        // Start the GUI and pass the streams to it
//        new GpsGUI(gpsStreams);
    }

    public void createGUI() {
    }

    public void processGpsStreams() {
    }
}