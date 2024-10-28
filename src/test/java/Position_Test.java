import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class Position_Test {
    @Test
    public void testPositionConstructor() {
        Position position = new Position(45.0, -75.0, 100.0);
        assertEquals(45.0, position.latitude, 0.001);
        assertEquals(-75.0, position.longitude, 0.001);
        assertEquals(100.0, position.altitude, 0.001);
    }

    @Test
    public void testPositionEqualsSameObject() {
        Position position = new Position(45.0, -75.0, 100.0);
        assertEquals(position, position); // Compare the object to itself
    }

    @Test
    public void testPositionEqualsEqualObjects() {
        Position position1 = new Position(45.0, -75.0, 100.0);
        Position position2 = new Position(45.0, -75.0, 100.0);
        assertEquals(position1, position2);
    }

    @Test
    public void testPositionEqualsDifferentObjects() {
        Position position1 = new Position(45.0, -75.0, 100.0);
        Position position2 = new Position(46.0, -76.0, 200.0);
        assertNotEquals(position1, position2);
    }

    @Test
    public void testPositionHashCode() {
        Position position1 = new Position(45.0, -75.0, 100.0);
        Position position2 = new Position(45.0, -75.0, 100.0);
        assertEquals(position1.hashCode(), position2.hashCode()); // Hash code should match for equal objects
    }
}
