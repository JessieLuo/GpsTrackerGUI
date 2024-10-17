import javax.swing.*;
import java.awt.*;

public class GuiOutline {
    private final JFrame frame = new JFrame("GPS Tracking Application");

    public GuiOutline() {
        // Initialize the GUI components
        initializeComponents();
    }

    private void initializeComponents() {
        // Set up the main frame
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800); // Proper window size to accommodate components

        // 1. All Tracker Display Panel (Simulated Table) and Real-time Info
        JPanel allTrackerDisplayPanel = createTrackerDisplayPanel("All Tracker Display", false);
        JPanel allTrackerInfoPanel = createCurrentTrackerPanel();

        // 2. Filtered Tracker Display Panel and Control Panel
        JPanel filteredTrackerDisplayPanel = createTrackerDisplayPanel("Filtered Tracker Display", true);
        JPanel controlPanel = createControlPanel();

        // Combine the left display panel with its corresponding info panel using JSplitPane
        JSplitPane allTrackerSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, allTrackerDisplayPanel, allTrackerInfoPanel);
        allTrackerSplitPane.setResizeWeight(0.8); // Give more space to the display panel

        // Combine the right display panel with the control panel using JSplitPane
        JSplitPane filteredTrackerSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filteredTrackerDisplayPanel, controlPanel);
        filteredTrackerSplitPane.setResizeWeight(0.8); // Give more space to the display panel

        // Place the main panels side by side
        JPanel mainPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        mainPanel.add(allTrackerSplitPane);
        mainPanel.add(filteredTrackerSplitPane);

        frame.setLayout(new BorderLayout());
        frame.add(mainPanel, BorderLayout.CENTER);
    }

    private JPanel createTrackerDisplayPanel(String title, boolean includeDistance) {
        // Create a panel with a grid layout to simulate the table
        int columnCount = includeDistance ? 5 : 4; // Columns: Tracker No, Lat, Lon, Time, [Distance]
        JPanel panel = new JPanel(new GridLayout(10 + 1, columnCount, 5, 5)); // +1 for the header row
        panel.setBorder(BorderFactory.createTitledBorder(title));

        // Header row
        panel.add(new JLabel("Tracker No"));
        panel.add(new JLabel("Latitude"));
        panel.add(new JLabel("Longitude"));
        panel.add(new JLabel("Timestamp"));
        if (includeDistance) {
            panel.add(new JLabel("Distance"));
        }

        // Data rows (empty for modeling purposes)
        for (int i = 0; i < 10; i++) {
            panel.add(new JLabel(String.valueOf(i + 1))); // Display only the tracker number
            panel.add(new JTextField(10)); // Latitude field
            panel.add(new JTextField(10)); // Longitude field
            panel.add(new JTextField(12)); // Timestamp field
            if (includeDistance) {
                panel.add(new JTextField(8)); // Distance field
            }
        }

        return panel;
    }

    private JPanel createCurrentTrackerPanel() {
        // Create a panel for real-time tracker information
        JPanel panel = new JPanel(new GridLayout(1, 4, 5, 5)); // Single row for real-time data
        panel.setBorder(BorderFactory.createTitledBorder("Current Event"));

        // Labels to show tracker info
        panel.add(new JLabel("Tracker No:"));
        panel.add(new JTextField(10)); // Tracker number field
        panel.add(new JLabel("Latitude:"));
        panel.add(new JTextField(10)); // Latitude field
        panel.add(new JLabel("Longitude:"));
        panel.add(new JTextField(10)); // Longitude field
        panel.add(new JLabel("Timestamp:"));
        panel.add(new JTextField(12)); // Timestamp field

        return panel;
    }

    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new GridBagLayout());
        controlPanel.setBorder(BorderFactory.createTitledBorder("Control Panel"));
        controlPanel.setPreferredSize(new Dimension(1200, 150)); // Fixed height to prevent shrinking

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Add input fields and labels
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        controlPanel.add(new JLabel("Min Lat:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        controlPanel.add(createCompactTextField(), gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        controlPanel.add(new JLabel("Max Lat:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        controlPanel.add(createCompactTextField(), gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.3;
        controlPanel.add(new JLabel("Min Lon:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        controlPanel.add(createCompactTextField(), gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.3;
        controlPanel.add(new JLabel("Max Lon:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        controlPanel.add(createCompactTextField(), gbc);

        // Add button and label for setting restrictions
        gbc.gridx = 2; gbc.gridy = 0; gbc.gridheight = 2;
        gbc.weightx = 1.0; gbc.anchor = GridBagConstraints.CENTER;
        JButton setRestrictionButton = new JButton("Set");
        controlPanel.add(setRestrictionButton, gbc);

        gbc.gridy = 2; gbc.gridheight = 2;
        JLabel restrictionValueLabel = new JLabel("Current Restriction: None");
        controlPanel.add(restrictionValueLabel, gbc);

        return controlPanel;
    }

    private JTextField createCompactTextField() {
        JTextField textField = new JTextField(8);
        textField.setPreferredSize(new Dimension(80, 25)); // Limit the preferred size
        return textField;
    }

    public void show() {
        frame.setVisible(true);
    }
}
