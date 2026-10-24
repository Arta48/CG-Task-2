package ru.vsu.cs.PlotterApp;

import java.util.function.DoubleUnaryOperator;

/**
 * Парсер математических выражений методом рекурсивного спуска.
 * Преобразует строку в функцию f(x) -> DoubleUnaryOperator.
 * Поддерживает: +, -, *, /, ^, скобки, унарный плюс / минус,
 * целые / вещественные числа / научный формат, константы pi / e,
 * функции: sin, cos, tan, ctg, sqrt, abs, ln, log, exp.
 */
class MathParser {
    private final String str;
    private int pos = -1;
    private int ch;

    // Закрытый конструктор
    private MathParser(String str) {
        this.str = str;
    }

    // Разбирает формулу и возвращает готовую к вычислению функцию f(x)
    public static DoubleUnaryOperator parse(String expression) {
        return new MathParser(expression).parse();
    }

    private DoubleUnaryOperator parse() {
        nextChar();
        DoubleUnaryOperator result = parseExpression();
        if (pos < str.length()) {
            throw new IllegalArgumentException("Неожиданный символ: " + (char) ch);
        }
        return result;
    }

    private void nextChar() {
        ch = (++pos < str.length()) ? str.charAt(pos) : -1;
    }

    private boolean eat(int charToEat) {
        while (ch == ' ') {
            nextChar();
        }
        if (ch == charToEat) {
            nextChar();
            return true;
        }
        return false;
    }

    // Сложение и вычитание
    private DoubleUnaryOperator parseExpression() {
        DoubleUnaryOperator x = parseTerm();
        for (;;) {
            if (eat('+')) {
                var a = x;
                var b = parseTerm();
                x = v -> a.applyAsDouble(v) + b.applyAsDouble(v);
            } else if (eat('-')) {
                var a = x;
                var b = parseTerm();
                x = v -> a.applyAsDouble(v) - b.applyAsDouble(v);
            } else {
                return x;
            }
        }
    }

    // Умножение и деление
    private DoubleUnaryOperator parseTerm() {
        DoubleUnaryOperator x = parseFactor();
        for (;;) {
            if (eat('*')) {
                var a = x;
                var b = parseFactor();
                x = v -> a.applyAsDouble(v) * b.applyAsDouble(v);
            } else if (eat('/')) {
                var a = x;
                var b = parseFactor();
                x = v -> a.applyAsDouble(v) / b.applyAsDouble(v);
            } else {
                return x;
            }
        }
    }

    // Числа, скобки, функции, унарные знаки и степени
    private DoubleUnaryOperator parseFactor() {
        if (eat('+')) {
            return parseFactor(); // Унарный плюс
        }
        if (eat('-')) {
            var a = parseFactor();
            return v -> -a.applyAsDouble(v); // Унарный минус
        }

        DoubleUnaryOperator x;
        int startPos = this.pos;

        if (eat('(')) { // Выражение в скобках
            x = parseExpression();
            if (!eat(')')) {
                throw new IllegalArgumentException("Пропущена закрывающая скобка ')'");
            }
        } else if ((ch >= '0' && ch <= '9') || ch == '.') { // Число (целое или вещественное)
            while ((ch >= '0' && ch <= '9') || ch == '.') {
                nextChar();
            }
            if (ch == 'e' || ch == 'E') { // Экспоненциальный формат (например, 1e-3)
                nextChar();
                if (ch == '+' || ch == '-') {
                    nextChar();
                }
                while (ch >= '0' && ch <= '9') {
                    nextChar();
                }
            }
            double val = Double.parseDouble(str.substring(startPos, this.pos));
            x = v -> val;
        } else if (Character.isLetter(ch)) { // Переменная, константа или функция
            while (Character.isLetter(ch)) {
                nextChar();
            }
            String id = str.substring(startPos, this.pos).toLowerCase();

            switch (id) {
                case "x" -> x = v -> v;
                case "pi" -> x = v -> Math.PI;
                case "e" -> x = v -> Math.E;
                default -> {
                    if (!eat('(')) {
                        throw new IllegalArgumentException("Ожидалась '(' после функции " + id);
                    }
                    var arg = parseExpression();
                    if (!eat(')')) {
                        throw new IllegalArgumentException("Пропущена закрывающая скобка после " + id);
                    }
                    x = switch (id) {
                        case "sin" -> v -> Math.sin(arg.applyAsDouble(v));
                        case "cos" -> v -> Math.cos(arg.applyAsDouble(v));
                        case "tan", "tg" -> v -> Math.tan(arg.applyAsDouble(v));
                        case "ctg" -> v -> 1.0 / Math.tan(arg.applyAsDouble(v));
                        case "sqrt" -> v -> Math.sqrt(arg.applyAsDouble(v));
                        case "abs" -> v -> Math.abs(arg.applyAsDouble(v));
                        case "ln" -> v -> Math.log(arg.applyAsDouble(v));
                        case "log" -> v -> Math.log10(arg.applyAsDouble(v));
                        case "exp" -> v -> Math.exp(arg.applyAsDouble(v));
                        default -> throw new IllegalArgumentException("Неизвестная функция: " + id);
                    };
                }
            }
        } else {
            throw new IllegalArgumentException("Неверный символ или синтаксис: " + (char) ch);
        }

        // Возведение в степень
        if (eat('^')) {
            var a = x;
            var b = parseFactor();
            x = v -> Math.pow(a.applyAsDouble(v), b.applyAsDouble(v));
        }

        return x;
    }
}
