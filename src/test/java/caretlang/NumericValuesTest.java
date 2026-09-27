package caretlang;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

final class NumericValuesTest {
    @Test
    void quotientRoundsLargeExactIntegersDirectlyToBinary64() {
        BigInteger midpoint = BigInteger.ONE.shiftLeft(53);
        assertEquals(1.0, NumericValues.quotientToDouble(midpoint.add(BigInteger.ONE), midpoint));
        assertEquals(Math.nextUp(1.0),
                NumericValues.quotientToDouble(midpoint.add(BigInteger.TWO), midpoint));
        BigInteger huge = BigInteger.TEN.pow(400);
        assertEquals(1.0, NumericValues.quotientToDouble(huge.add(BigInteger.ONE), huge));
        assertEquals(Double.POSITIVE_INFINITY,
                NumericValues.quotientToDouble(huge, BigInteger.valueOf(7)));
        assertEquals(-0.0, NumericValues.quotientToDouble(BigInteger.ONE.negate(), huge));
        BigInteger halfSmallestSubnormal = BigInteger.ONE.shiftLeft(1075);
        assertEquals(0.0, NumericValues.quotientToDouble(BigInteger.ONE, halfSmallestSubnormal));
        assertEquals(Double.longBitsToDouble(2),
                NumericValues.quotientToDouble(BigInteger.valueOf(3), halfSmallestSubnormal));
    }
}
