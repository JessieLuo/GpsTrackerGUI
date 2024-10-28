import nz.sodium.Cell;
import org.junit.Test;
import swidgets.STextField;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class GpsInputConv_Test {
    @Test
    public void testConvertInputs_ValidWithinRange() {
        // Initialize the textField with a valid input within range
        STextField textField = new STextField("25.5");

        // Test conversion within range
        Cell<Optional<Double>> result = Utils.convertInputs(textField, 0, 50);
        assertEquals(Optional.of(25.5), result.sample());
    }

    @Test
    public void testConvertInputs_EmptyString() {
        // Initialize the textField with an empty string
        STextField textField = new STextField("");

        // Test conversion with empty input
        Cell<Optional<Double>> result = Utils.convertInputs(textField, 0, 50);
        assertEquals(Optional.empty(), result.sample());
    }

    @Test
    public void testConvertInputs_OutOfRangeHigh() {
        // Initialize the textField with a value out of the specified range
        STextField textField = new STextField("55.0");

        // Test conversion for out-of-range input
        Cell<Optional<Double>> result = Utils.convertInputs(textField, 0, 50);
        assertEquals(Optional.empty(), result.sample());
    }

    @Test
    public void testConvertInputs_NonNumeric() {
        // Initialize the textField with non-numeric input
        STextField textField = new STextField("abc");

        // Test conversion with non-numeric input
        Cell<Optional<Double>> result = Utils.convertInputs(textField, 0, 50);
        assertEquals(Optional.empty(), result.sample());
    }

    @Test
    public void testConvertInputs_WhitespaceTrim() {
        // Initialize the textField with a valid input surrounded by whitespace
        STextField textField = new STextField(" 30.5 ");

        // Test that whitespace is trimmed and value is within range
        Cell<Optional<Double>> result = Utils.convertInputs(textField, 0, 50);
        assertEquals(Optional.of(30.5), result.sample());
    }

}
