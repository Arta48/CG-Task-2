package ru.vsu.cs.PlotterApp;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.function.DoubleUnaryOperator;

public class PlotterApp extends Application {
    private final Canvas canvas = new Canvas();
    private DoubleUnaryOperator func = null;
    private double originX = 400;
    private double originY = 300;
    private double scale = 40.0;
    private double dragStartX;
    private double dragStartY;
    private boolean initialized = false;

    @Override
    public void start(Stage stage) {
        TextField tfExpr = new TextField("sin(x) * x");
        tfExpr.setPrefWidth(300);
        Button btnPlot = new Button("Построить");
        Label lblErr = new Label();
        lblErr.setTextFill(Color.RED);

        Runnable applyFunc = () -> {
            try {
                // func = MathParser.parse(tfExpr.getText());
                lblErr.setText("");
            } catch (Exception ex) {
                lblErr.setText("Ошибка: " + ex.getMessage());
            }
            redraw();
        };

        btnPlot.setOnAction(e -> applyFunc.run());
        tfExpr.setOnAction(e -> applyFunc.run());

        Pane canvasPane = new Pane(canvas);
        canvas.widthProperty().bind(canvasPane.widthProperty());
        canvas.heightProperty().bind(canvasPane.heightProperty());
        canvas.widthProperty().addListener((o, oldV, newV) -> {
            if (!initialized && newV.doubleValue() > 0) {
                originX = canvas.getWidth() / 2;
                originY = canvas.getHeight() / 2;
                initialized = true;
            }
            redraw();
        });
        canvas.heightProperty().addListener(e -> redraw());

        // Перемещение мышью
        canvas.setOnMousePressed(e -> {
            dragStartX = e.getX() - originX;
            dragStartY = e.getY() - originY;
        });
        canvas.setOnMouseDragged(e -> {
            originX = e.getX() - dragStartX;
            originY = e.getY() - dragStartY;
            redraw();
        });

        // Масштабирование
        canvas.setOnScroll(e -> {
            if (e.getDeltaY() == 0) {
                return;
            }
            // Зум: 1.15 при приближении и 1 / 1.15 при отдалении
            double factor = e.getDeltaY() > 0 ? 1.15 : (1.0 / 1.15);
            zoom(factor, e.getX(), e.getY());
        });

        HBox controls = new HBox(10, new Label("f(x) ="), tfExpr, btnPlot, lblErr);
        controls.setPadding(new Insets(10));
        BorderPane root = new BorderPane(canvasPane);
        root.setTop(controls);

        applyFunc.run();
        Scene scene = new Scene(root, 900, 650);
        scene.setOnKeyPressed(e -> {
            if (e.isControlDown()) {
                double cx = canvas.getWidth() / 2, cy = canvas.getHeight() / 2;
                if (e.getCode() == KeyCode.PLUS || e.getCode() == KeyCode.ADD || e.getCode() == KeyCode.EQUALS) {
                    zoom(1.15, cx, cy);
                } else if (e.getCode() == KeyCode.MINUS || e.getCode() == KeyCode.SUBTRACT) {
                    zoom(1.0 / 1.15, cx, cy);
                }
            }
        });
        stage.setScene(scene);
        stage.setTitle("Plotter App");
        stage.show();
    }

    private void zoom(double factor, double cx, double cy) {
        double newScale = scale * factor;
        // Ограничение scale от 1.5 до 100 000
        if (newScale < 1.5 || newScale > 100_000) {
            return;
        }

        originX = cx + (originX - cx) * factor;
        originY = cy + (originY - cy) * factor;
        scale = newScale;
        redraw();
    }

    private void redraw() {
        double w = canvas.getWidth(), h = canvas.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, w, h);

        // Отрисовка динамической сетки
        double step = 1;
        while (step * scale < 40) {
            step *= 2;
        }
        while (step * scale > 100) {
            step /= 2;
        }

        gc.setStroke(Color.rgb(230, 230, 230));
        gc.setLineWidth(1);
        for (double x = (originX % (step * scale)) - step * scale; x < w; x += step * scale) {
            gc.strokeLine(x, 0, x, h);
        }
        for (double y = (originY % (step * scale)) - step * scale; y < h; y += step * scale) {
            gc.strokeLine(0, y, w, y);
        }

        // Оси координат
        gc.setStroke(Color.GRAY);
        gc.setLineWidth(1.5);
        gc.strokeLine(0, originY, w, originY);
        gc.strokeLine(originX, 0, originX, h);

        // Подписи делений (единичных отрезков) на осях
        gc.setFill(Color.DARKGRAY);
        for (double x = (originX % (step * scale)) - step * scale; x < w; x += step * scale) {
            double v = Math.round((x - originX) / scale * 1e4) / 1e4;
            if (Math.abs(v) > 1e-5) {
                gc.fillText(v == (long) v ? "" + (long) v : "" + v, x + 2, Math.clamp(originY + 12, 12, h - 4));
            }
        }
        for (double y = (originY % (step * scale)) - step * scale; y < h; y += step * scale) {
            double v = Math.round((originY - y) / scale * 1e4) / 1e4;
            if (Math.abs(v) > 1e-5) {
                gc.fillText(v == (long) v ? "" + (long) v : "" + v, Math.clamp(originX + 4, 4, w - 35), y - 2);
            }
        }
        gc.fillText("0", originX + 3, originY + 12);

        // Отрисовка графика функции
        if (func == null) {
            return;
        }
        gc.setStroke(Color.ROYALBLUE);
        gc.setLineWidth(2.0);

        boolean prevOk = false;
        double prevX = 0;
        double prevY = 0;
        double prevMathY = 0;

        for (double px = 0; px <= w; px += 0.5) {
            double mathX = (px - originX) / scale;
            try {
                double mathY = func.applyAsDouble(mathX);
                if (Double.isFinite(mathY)) {
                    double rawPy = originY - mathY * scale;
                    double py = Math.max(-h * 2, Math.min(h * 3, rawPy));

                    if (prevOk) {
                        // Асимптота при смене знака: значение в средней точке улетает за пределы диапазона
                        double midY = func.applyAsDouble((px - 0.25 - originX) / scale);
                        boolean isAsymptote = (prevMathY * mathY < 0) && (!Double.isFinite(midY) || midY < Math.min(prevMathY, mathY) || midY > Math.max(prevMathY, mathY));

                        if (isAsymptote) {
                            gc.strokeLine(prevX, prevY, prevX, prevMathY > 0 ? -10 : h + 10);
                            gc.strokeLine(px, mathY > 0 ? -10 : h + 10, px, py);
                        } else {
                            // Обычная непрерывная линия
                            gc.strokeLine(prevX, prevY, px, py);
                        }
                    }
                    prevX = px;
                    prevY = py;
                    prevMathY = mathY;
                    prevOk = true;
                    continue;
                }
            } catch (Exception ignored) {}
            prevOk = false;
        }
    }
}
