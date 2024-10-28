import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EventProcessor_Test {
    /* Test Display (2) */

    /* Test distance calculate method */
    @Test
    public void testDistanceWithSamePosition() {
        Position pos1 = new Position(45.0, -75.0, 100.0);
        Position pos2 = new Position(45.0, -75.0, 100.0);

        double distance = EventProcessor.calculateDistance(pos1, pos2);

        assertEquals(0.0, distance, 0.001);  // Expecting 0 meters distance for identical positions
    }

    @Test
    public void testDistanceWithOnlyAltitudeDifference() {
        Position pos1 = new Position(45.0, -75.0, 100.0);
        Position pos2 = new Position(45.0, -75.0, 200.0);

        double distance = EventProcessor.calculateDistance(pos1, pos2);

        assertEquals(100.0, distance, 0.001);  // Only altitude difference, so distance should equal altitude difference
    }

    @Test
    public void testDistanceWithHorizontalAndAltitudeDifference() {
        Position pos1 = new Position(45.0, -75.0, 100.0);
        Position pos2 = new Position(45.001, -75.001, 200.0);

        double distance = EventProcessor.calculateDistance(pos1, pos2);

        // Calculated manually or estimated expected value for the given lat/lon/altitude difference
        assertEquals(168.95, distance, 0.1);  // Adjust tolerance based on accuracy required
    }

    @Test
    public void testDistanceWithNullPositions() {
        Position pos2 = new Position(45.0, -75.0, 100.0);

        double distance = EventProcessor.calculateDistance(null, pos2);

        assertEquals(0.0, distance, 0.001);  // Distance should be 0 when any position is null
    }

}