import nz.sodium.Stream;

/**
 * The Main method to activate GUI
 */
public class GpsGUI {

    public static void main(String[] args) {
        // Initialize the GPS Service
        GpsService gpsService = new GpsService();

        // Retrieve the event streams from GpsService
        Stream<GpsEvent>[] gpsStreams = gpsService.getEventStreams();

        // Display the GUI
        GuiOutline gui = new GuiOutline(gpsStreams);
        gui.show();
    }
}