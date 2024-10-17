import javax.swing.*;
import java.awt.*;

public class GuiFrame {
    private final JFrame frame = new JFrame("GPS Tracking GUI");
    // Create the main panels
    private final JPanel trackerPanel = new JPanel(new GridLayout(10, 1));
    private final JPanel combinedPanel = new JPanel(new BorderLayout());
    private final JPanel controlPanel = new JPanel(new GridLayout(3, 2));

    public GuiFrame() {
        // Initialize the GUI components
        initializeComponents();
    }

    private void initializeComponents() {
        // Create the main frame
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        // Create the tracker displays
        for (int i = 1; i <= 10; i++) {
            JPanel trackerDisplay = new JPanel(new FlowLayout(FlowLayout.LEFT));
            trackerDisplay.setBorder(BorderFactory.createTitledBorder("Tracker " + i));
            JLabel trackerLabel = new JLabel("Event: ");
            JTextField trackerField = new JTextField(30);
            trackerField.setEditable(false);
            trackerDisplay.add(trackerLabel);
            trackerDisplay.add(trackerField);
            trackerPanel.add(trackerDisplay);
        }

        // Create the combined display
        JPanel combinedDisplay = new JPanel(new FlowLayout(FlowLayout.LEFT));
        combinedDisplay.setBorder(BorderFactory.createTitledBorder("Combined Tracker Display"));
        JLabel combinedLabel = new JLabel("Event: ");
        JTextField combinedField = new JTextField(30);
        combinedField.setEditable(false);
        combinedDisplay.add(combinedLabel);
        combinedDisplay.add(combinedField);

        // Create the distance display
        JPanel distanceDisplay = new JPanel(new GridLayout(10, 1));
        distanceDisplay.setBorder(BorderFactory.createTitledBorder("Distance Traveled (last 5 minutes)"));
        for (int i = 1; i <= 10; i++) {
            JLabel distanceLabel = new JLabel("Tracker " + i + ": ");
            JTextField distanceField = new JTextField(10);
            distanceField.setEditable(false);
            JPanel distancePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            distancePanel.add(distanceLabel);
            distancePanel.add(distanceField);
            distanceDisplay.add(distancePanel);
        }

        // Add the combined display and distance display to the combined panel
        combinedPanel.add(combinedDisplay, BorderLayout.NORTH);
        combinedPanel.add(distanceDisplay, BorderLayout.CENTER);

        // Create the control panel for latitude/longitude settings
        JLabel latLabel = new JLabel("Latitude:");
        JTextField latField = new JTextField(10);
        JLabel lonLabel = new JLabel("Longitude:");
        JTextField lonField = new JTextField(10);
        JButton setButton = new JButton("Set Restriction");

        controlPanel.add(latLabel);
        controlPanel.add(latField);
        controlPanel.add(lonLabel);
        controlPanel.add(lonField);
        controlPanel.add(new JLabel()); // Empty cell for layout alignment
        controlPanel.add(setButton);

        // Add components to the main frame
        frame.add(trackerPanel, BorderLayout.WEST);
        frame.add(combinedPanel, BorderLayout.CENTER);
        frame.add(controlPanel, BorderLayout.SOUTH);
    }

    public void show() {
        frame.setVisible(true);
    }
}
