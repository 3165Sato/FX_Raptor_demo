package com.example.calculator;

public class Calculator {

    public double add(double left, double right) {
        return left + right;
    }

    public double subtract(double left, double right) {
        return left - right;
    }

    public double multiply(double left, double right) {
        return left * right;
    }

    public double divide(double left, double right) {
        if (right == 0) {
            throw new IllegalArgumentException("0で割ることはできません。");
        }
        return left / right;
    }

    public double calculate(double left, String operator, double right) {
        return switch (operator) {
            case "+" -> add(left, right);
            case "-" -> subtract(left, right);
            case "*" -> multiply(left, right);
            case "/" -> divide(left, right);
            default -> throw new IllegalArgumentException("未対応の演算子です: " + operator);
        };
    }
}
