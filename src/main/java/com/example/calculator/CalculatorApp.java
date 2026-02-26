package com.example.calculator;

import java.util.Scanner;

public class CalculatorApp {

    public static void main(String[] args) {
        Calculator calculator = new Calculator();

        if (args.length == 3) {
            runWithArgs(calculator, args);
            return;
        }

        runInteractive(calculator);
    }

    private static void runWithArgs(Calculator calculator, String[] args) {
        try {
            double left = Double.parseDouble(args[0]);
            String operator = args[1];
            double right = Double.parseDouble(args[2]);

            double result = calculator.calculate(left, operator, right);
            System.out.println("結果: " + result);
        } catch (NumberFormatException e) {
            System.err.println("数値の形式が正しくありません。例: 10 + 2");
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void runInteractive(Calculator calculator) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("四則演算アプリ (終了するには q を入力)");

        while (true) {
            System.out.print("1つ目の数値: ");
            String leftInput = scanner.nextLine().trim();
            if ("q".equalsIgnoreCase(leftInput)) {
                break;
            }

            System.out.print("演算子 (+, -, *, /): ");
            String operator = scanner.nextLine().trim();

            System.out.print("2つ目の数値: ");
            String rightInput = scanner.nextLine().trim();

            try {
                double left = Double.parseDouble(leftInput);
                double right = Double.parseDouble(rightInput);
                double result = calculator.calculate(left, operator, right);
                System.out.println("結果: " + result);
            } catch (NumberFormatException e) {
                System.out.println("数値を正しく入力してください。");
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            }

            System.out.println();
        }

        System.out.println("アプリを終了します。");
    }
}
