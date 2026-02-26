package com.example.calculator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculatorTest {

    private final Calculator calculator = new Calculator();

    @Test
    void addReturnsSum() {
        assertEquals(7.0, calculator.add(3, 4));
    }

    @Test
    void subtractReturnsDifference() {
        assertEquals(5.0, calculator.subtract(8, 3));
    }

    @Test
    void multiplyReturnsProduct() {
        assertEquals(24.0, calculator.multiply(6, 4));
    }

    @Test
    void divideReturnsQuotient() {
        assertEquals(2.5, calculator.divide(5, 2));
    }

    @Test
    void divideByZeroThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> calculator.divide(5, 0));
    }

    @Test
    void calculateWithUnsupportedOperatorThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(1, "%", 2));
    }
}
