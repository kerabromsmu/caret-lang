package caretlang;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Mathematical comparisons and exact-integer operations independent of a number's carrier. */
final class NumericValues {
    private NumericValues() {}

    static BigDecimal decimal(Value.Num number) {
        return number.exactInteger() != null
                ? new BigDecimal(number.exactInteger()) : new BigDecimal(number.value());
    }

    static BigInteger integral(Value.Num number) {
        if (number.exactInteger() != null) return number.exactInteger();
        if (number.value() != Math.rint(number.value())) return null;
        return new BigDecimal(number.value()).toBigIntegerExact();
    }

    static int compare(Value.Num left, Value.Num right) {
        return decimal(left).compareTo(decimal(right));
    }

    static boolean exactlyDouble(Value.Num value) {
        if (value.exactInteger() == null) return true;
        return Double.isFinite(value.value())
                && decimal(value).compareTo(new BigDecimal(value.value())) == 0;
    }

    static boolean exactlyFloat(Value.Num value) {
        double approximation = value.value();
        float narrowed = (float) approximation;
        return Float.isFinite(narrowed)
                && decimal(value).compareTo(new BigDecimal((double) narrowed)) == 0;
    }

    static boolean fitsInteger(Value.Num value, int width, boolean signed) {
        BigInteger integer = integral(value);
        if (integer == null) return false;
        BigInteger minimum = signed ? BigInteger.ONE.shiftLeft(width - 1).negate() : BigInteger.ZERO;
        BigInteger maximum = BigInteger.ONE.shiftLeft(signed ? width - 1 : width).subtract(BigInteger.ONE);
        return integer.compareTo(minimum) >= 0 && integer.compareTo(maximum) <= 0;
    }

    static int nonNegativeInt(Value value) {
        if (!(ValueSemantics.underlying(value) instanceof Value.Num number)) return -1;
        BigInteger integer = integral(number);
        return integer != null && integer.signum() >= 0
                && integer.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0
                ? integer.intValue() : -1;
    }

    static double quotientToDouble(BigInteger numerator, BigInteger denominator) {
        if (numerator.signum() == 0) return 0.0;
        boolean negative = numerator.signum() != denominator.signum();
        BigInteger a = numerator.abs();
        BigInteger b = denominator.abs();
        int exponent = a.bitLength() - b.bitLength();
        if (exponent > 1024) return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        if (exponent < -1076) return negative ? -0.0 : 0.0;
        if (exponent >= 0) {
            if (a.compareTo(b.shiftLeft(exponent)) < 0) exponent--;
        } else if (a.shiftLeft(-exponent).compareTo(b) < 0) {
            exponent--;
        }
        if (exponent > 1023) return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        if (exponent < -1075) return negative ? -0.0 : 0.0;
        int scale = exponent >= -1022 ? 52 - exponent : 1074;
        BigInteger scaledNumerator = scale >= 0 ? a.shiftLeft(scale) : a;
        BigInteger scaledDenominator = scale < 0 ? b.shiftLeft(-scale) : b;
        BigInteger[] division = scaledNumerator.divideAndRemainder(scaledDenominator);
        int halfway = division[1].shiftLeft(1).compareTo(scaledDenominator);
        BigInteger mantissa = halfway > 0 || halfway == 0 && division[0].testBit(0)
                ? division[0].add(BigInteger.ONE) : division[0];
        double rounded = Math.scalb(mantissa.doubleValue(), -scale);
        return negative ? -rounded : rounded;
    }
}
