package com.bosonshiggs.calculatecanvasarea;

import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.runtime.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.util.MediaUtil;
import com.google.appinventor.components.annotations.Asset;
import com.google.appinventor.components.runtime.util.YailList;

import android.content.Context;
import android.view.View;
import android.view.ViewTreeObserver;
import android.graphics.Bitmap;
import android.graphics.Paint;

import android.os.Build;

import android.graphics.PorterDuff;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.FileOutputStream;

import java.util.ArrayDeque;
import java.util.Deque;

import java.util.ArrayList;
import java.util.List;

import java.util.Collections;
import java.util.Comparator;

import android.util.Log;

@DesignerComponent(version = 1,
    description = "Extension to draw and calculate areas on the Canvas with reference lines",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/icon.png")
@SimpleObject(external = true)
public class CalculateCanvasArea extends AndroidNonvisibleComponent {
	private ArrayList<double[]> areaPoints = new ArrayList<>();
    
	private double[] startRedLine = new double[2], endRedLine = new double[2];
    private double[] startBlueLine = new double[2], endBlueLine = new double[2];    
    
    private double actualLengthRed, actualLengthBlue;
    
    private double lengthRedLineInMeters;
    private double lengthBlueLineInMeters;

    private ComponentContainer container;
    private double startXBlue, startXRed;
    private double currentXBlue, currentXRed;
    
    private double startYBlue, startYRed;
    private double currentYBlue, currentYRed;
    
    private Bitmap drawingBitmap;
    
    private Canvas canvasComponent;
    
    private Context context;
    
    private int canvasWidth = 800; // Largura padrão
    private int canvasHeight = 600; // Altura padrão
    
    private int lineColor;
    
    private Point lineStartPoint = new Point(0, 0);
    private Point lineEndPoint = new Point(0, 0);
    
    private String LOG_NAME = "CalculateCanvasArea";
    private boolean flagLog = false;

    public CalculateCanvasArea(ComponentContainer container) {
        super(container.$form());
        this.container = container;
        this.context = container.$context();
        
        drawingBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
    }
    
    @SimpleFunction(description = "Sets the red reference line as the horizontal reference.")
    public void SetRedLine(double startX, double startY, double currentX, double currentY, double lengthMeters) {
    	startXRed = startX;
    	startYRed = startY;
    	currentXRed = currentX;
    	currentYRed = currentY;
    	lengthRedLineInMeters = lengthMeters;
    	
    	// Start line
    	lineStartPoint = new Point(startX, startY);
    	// Update line
    	lineEndPoint = new Point(currentX, currentY);

    	// Desenha a reta
    	lineColor = Color.RED;
    	RedrawCanvas();
    }
    
    @SimpleFunction(description = "End the red line.")
    public void EndRedLine() {
    	startRedLine[0] = startXRed;
        startRedLine[1] = startYRed;
        endRedLine[0] = currentXRed;
        endRedLine[1] = currentYRed;
        DrawLine(lineColor);
    }

    @SimpleFunction(description = "Sets the blue reference line as the vertical reference.")
    public void SetBlueLine(double startX, double startY, double currentX, double currentY, double lengthMeters) {
    	startXBlue = startX;
    	startYBlue = startY;
    	currentXBlue = currentX;
    	currentYBlue = currentY;
    	lengthBlueLineInMeters = lengthMeters;

    	// Start line
    	lineStartPoint = new Point(startX, startY);
    	// Update line
    	lineEndPoint = new Point(currentX, currentY);

    	// Desenha a reta
    	lineColor = Color.BLUE;
    	RedrawCanvas();
    }

    @SimpleFunction(description = "End the blue line.")
    public void EndBlueLine() {
    	startBlueLine[0] = startXBlue;
        startBlueLine[1] = startYBlue;
        endBlueLine[0] = currentXBlue;
        endBlueLine[1] = currentYBlue;
        DrawLine(lineColor);
    }
    
    private void DrawLine(int color) {
        if (lineStartPoint != null && lineEndPoint != null) {
            try {
                android.graphics.Canvas canvas = new android.graphics.Canvas(drawingBitmap);
                Paint paint = new Paint();
                paint.setColor(color);
                paint.setStrokeWidth(5);

                canvas.drawLine((float) lineStartPoint.getX(), (float) lineStartPoint.getY(), 
                                (float) lineEndPoint.getX(), (float) lineEndPoint.getY(), paint);

                // Resto do método...
            } catch (IllegalArgumentException e) {
            	if (flagLog) Log.e("CalculateCanvasArea", "Erro ao desenhar linha: " + e.getMessage());
                ReportError("Erro ao desenhar linha: " + e.getMessage());
            }
        }
    }

    @SimpleFunction(description = "Sets the actual lengths of the reference lines")
    public void SetActualLengths(double lengthRed, double lengthBlue) {
        this.actualLengthRed = lengthRed;
        this.actualLengthBlue = lengthBlue;
    }

    @SimpleFunction(description = "Calculates the area of the green region")
    public double CalculateArea() {
        // Verifica se existem pontos suficientes para formar uma área
        if (areaPoints.size() < 3) return 0.0;
        
        sortPointsForAreaCalculation(); // Ordena os pontos antes de calcular a área

        double areaInDegrees;
        try {
            areaInDegrees = AreaCalculator.calculateArea(areaPoints);
        } catch (Exception e) {
        	if (flagLog) Log.e(LOG_NAME, "Error: " + e.getMessage(), e);
        	ReportError("Erro ao calcular a área");
            return 0.0;
        }

        if (startRedLine == null || endRedLine == null || startBlueLine == null || endBlueLine == null) {
            // Se as linhas de referência não estiverem definidas, retorna 0 ou lida com o erro conforme necessário
            return 0;
        }

        double horizontalScale = calculateScale(startRedLine, endRedLine, lengthRedLineInMeters);
        double verticalScale = calculateScale(startBlueLine, endBlueLine, lengthBlueLineInMeters);

        // Retorna a área calculada ajustada pelas escalas
        return areaInDegrees * horizontalScale * verticalScale;
    }

    private double calculateScale(double[] start, double[] end, double lengthMeters) {
        if (start == null || end == null) {
            // Handle the error or return a default value
            return 0; // Or throw an exception, based on how you want to handle this scenario
        }
        double distanceInDegrees = Math.sqrt(Math.pow(end[0] - start[0], 2) + Math.pow(end[1] - start[1], 2));
        return distanceInDegrees == 0 ? 0 : lengthMeters / distanceInDegrees;
    }

    @SimpleFunction(description = "Loads an image from a path and converts it to grayscale")
    public String ConvertToGrayscale(@Asset String imagePath) {
        final String tempPath = imagePath == null ? "" : imagePath;
        try {
            Drawable drawable = MediaUtil.getBitmapDrawable(container.$form(), imagePath);
            Bitmap originalBitmap = ((BitmapDrawable) drawable).getBitmap();

            Bitmap grayscaleBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);

            for (int i = 0; i < originalBitmap.getWidth(); i++) {
                for (int j = 0; j < originalBitmap.getHeight(); j++) {
                    int pixel = originalBitmap.getPixel(i, j);
                    int average = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                    grayscaleBitmap.setPixel(i, j, Color.rgb(average, average, average));
                }
            }

            // Saves the bitmap to a temporary file
            String path = container.$form().getExternalFilesDir(null).getAbsolutePath() + "/grayscale_image.png";
            OutputStream outputStream = new FileOutputStream(path);
            grayscaleBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.close();

            return path; // Returns the path of the converted image
        } catch (IOException e) {
            e.printStackTrace();
            return ""; // Returns an empty string in case of error
        }
    }

    @SimpleFunction(description = "Resets the points in the green area.")
    public void ResetAreaPoints() {
    	areaPoints.clear();
    }

    @SimpleFunction(description = "Calculates the distance between two points.")
    public double CalculateDistanceBetweenPoints(double x1, double y1, double x2, double y2) {
        // Calcula a distância euclidiana entre os dois pontos
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }


    @SimpleFunction(description = "Gets the total number of points in the green area.")
    public int GetTotalPointsCount() {
    	return areaPoints.size(); // Corrigido para 'areaPoints'
    }

    @SimpleFunction(description = "Calculates the perimeter of the green area.")
    public double GetAreaPerimeter() {
        double perimeter = 0.0;
        for (int i = 0; i < areaPoints.size(); i++) { // Corrigido para 'areaPoints'
            double[] current = areaPoints.get(i);
            double[] next = areaPoints.get((i + 1) % areaPoints.size()); // Corrigido para 'areaPoints'
            perimeter += CalculateDistanceBetweenPoints(current[0], current[1], next[0], next[1]);
        }
        return perimeter;
    }
    
    // Exemplo de método de desenho (precisa ser expandido)
    @SimpleFunction(description = "Draw a line on the active layer.")
    public void DrawLine(double prevX, double prevY, double currentX, double currentY, int color, float strokeWidth) {
        if (drawingBitmap == null) {
        	if (flagLog) Log.e(LOG_NAME, "Bitmap for active layer is null.");
            ReportError("Bitmap for active layer is null.");
            return;
        }
        
        AddAreaPoint(currentX, currentY);
        
        android.graphics.Canvas canvas = new android.graphics.Canvas(drawingBitmap);
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(strokeWidth);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setAntiAlias(true);
        
        canvas.drawLine((float) prevX, (float) prevY, (float) currentX, (float) currentY, paint);
        RedrawCanvas();
    }
    
    @SimpleFunction(description = "Clears the active layer.")
    public void ClearCanvas() {
        if (drawingBitmap != null) {
            // Limpar o Bitmap da camada ativa
        	android.graphics.Canvas canvas = new android.graphics.Canvas(drawingBitmap);
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            RedrawCanvas();
        }
    }
    
    @SimpleFunction(description = "Returns the area points as a List.")
    public YailList GetAreaPointsAsList() {
        List<YailList> yailPoints = new ArrayList<>();
        for (double[] point : areaPoints) {
            yailPoints.add(YailList.makeList(new Object[]{point[0], point[1]}));
        }
        return YailList.makeList(yailPoints);
    }
    
    @SimpleFunction(description = "Checks whether a point is inside the polygon.")
    public boolean IsPointInPolygon(double x, double y) {
        boolean inside = false;
        int n = areaPoints.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            if ((areaPoints.get(i)[1] > y) != (areaPoints.get(j)[1] > y) &&
                (x < (areaPoints.get(j)[0] - areaPoints.get(i)[0]) * (y - areaPoints.get(i)[1]) / (areaPoints.get(j)[1]-areaPoints.get(i)[1]) + areaPoints.get(i)[0])) {
                inside = !inside;
            }
        }
        return inside;
    }

    @SimpleFunction(description = "Undoes the addition of the last point.")
    public void UndoLastPoint() {
        if (!areaPoints.isEmpty()) {
            areaPoints.remove(areaPoints.size() - 1);
        }
    }

    @SimpleFunction(description = "Imports points into the polygon.\n"
    		+ "Example: [[x1, y1], [x2, y2], etc]")
    public void ImportPoints(YailList pointsList) {
        ResetAreaPoints();
        for (Object o : pointsList.toArray()) {
            if (o instanceof YailList) {
                YailList point = (YailList) o;
                if (point.size() == 2) {
                    AddAreaPoint(((Number) point.get(1)).doubleValue(), ((Number) point.get(2)).doubleValue());
                }
            }
        }
    }
    
    @SimpleFunction(description = "Set canvas dimensions.")
    public void SetCanvasSize(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) {
        	if (flagLog) Log.e(LOG_NAME, "Dimensões inválidas para o bitmap: Largura e altura devem ser positivas.");
            ReportError("Dimensões inválidas para o bitmap: Largura e altura devem ser positivas.");
            return;
        }

        try {
            // Redimensiona o bitmap mantendo seu conteúdo
            Bitmap resizedBitmap = Bitmap.createBitmap(newWidth, newHeight, drawingBitmap.getConfig());
            android.graphics.Canvas canvas = new android.graphics.Canvas(resizedBitmap);
            canvas.drawColor(Color.TRANSPARENT);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(drawingBitmap, newWidth, newHeight, true);
            canvas.drawBitmap(scaledBitmap, 0, 0, null);

            // Atualiza as variáveis de largura e altura do canvas
            canvasWidth = newWidth;
            canvasHeight = newHeight;

            // Substitui o bitmap de desenho
            drawingBitmap = resizedBitmap;

            // Redesenha o canvas se necessário
            RedrawCanvas();
        } catch (Exception e) {
        	if (flagLog) Log.e(LOG_NAME, "Erro ao redimensionar o bitmap: " + e.getMessage(), e);
            ReportError("Erro ao redimensionar o bitmap.");
        }
    }
    
    /*
     * PROPERTY
     */
    
    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Loads an image from a path and adds it to the active layer.")
    public void AddBackgroundImage(@Asset final String imagePath) {
        if (drawingBitmap == null) {
        	if (flagLog) Log.e(LOG_NAME, "No active layer to add image.");
            ReportError("No active layer to add image.");
            return;
        }

        try {
            Bitmap imageBitmap = BitmapFactory.decodeFile(imagePath);

            if (imageBitmap == null) {
                try {
                    Drawable drawable = MediaUtil.getBitmapDrawable(container.$form(), imagePath);
                    imageBitmap = ((BitmapDrawable) drawable).getBitmap();
                } catch (Exception e) {
                    throw new Exception("Failed to load image.");
                }
            }

            // Redimensionar a imagem para o tamanho do Canvas
            Bitmap resizedImageBitmap = Bitmap.createScaledBitmap(imageBitmap, canvasWidth, canvasHeight, true);

            // Create a canvas to draw on the layer
            android.graphics.Canvas canvas = new android.graphics.Canvas(drawingBitmap);
            canvas.drawBitmap(resizedImageBitmap, 0, 0, null); // Draw the resized image

            // Redraw the canvas
            RedrawCanvas();
        } catch (Exception e) {
        	if (flagLog) Log.e(LOG_NAME, "Error adding image to layer: " + e.getMessage(), e);
            ReportError("Error adding image to layer: " + e.getMessage());
        }
    }
    
    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Sets the Canvas component used for drawing.")
    public void SetCanvas(Canvas canvas) {
        this.canvasComponent = canvas;
        SetCanvasMonitoring(canvas);
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR)
    public int GetCanvasWidth() {
        return this.canvasWidth;
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR)
    public int GetCanvasHeight() {
        return this.canvasHeight;
    }

    /*
     * EVENTS
     */
    @SimpleEvent(description = "Report an error with a custom message")
    public void ReportError(String errorMessage) {
        EventDispatcher.dispatchEvent(this, "ReportError", errorMessage);
    }

    @SimpleEvent(description = "Triggered when the Canvas size changes.")
    public void CanvasSizeChanged(int width, int height) {
        EventDispatcher.dispatchEvent(this, "CanvasSizeChanged", width, height);
    }

    /*
     * PRIVATE METHODS
     */

    private void RedrawCanvas() {
        if (canvasComponent != null) {
            Bitmap finalBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas finalCanvas = new android.graphics.Canvas(finalBitmap);

            // Desenha todas as camadas
            finalCanvas.drawBitmap(drawingBitmap, 0, 0, null);

            // Desenha a linha temporaria
            Paint linePaint = new Paint();
            linePaint.setColor(lineColor); // Usa a cor definida
            linePaint.setStrokeWidth(5); // Usa a espessura definida
            finalCanvas.drawLine((float) lineStartPoint.getX(), (float) lineStartPoint.getY(), (float) lineEndPoint.getX(), (float) lineEndPoint.getY(), linePaint);

            // Atualiza o Canvas do componente
            canvasComponent.getView().setBackground(new BitmapDrawable(context.getResources(), finalBitmap));
            canvasComponent.getView().invalidate();
        }
    }

    public void SetCanvasMonitoring(final Canvas canvas) {
        final View view = canvas.getView();

        // Posterga a verificação até que a View esteja completamente carregada
        view.post(new Runnable() {
            @Override
            public void run() {
                view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // Remove o listener para evitar chamadas múltiplas
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        } else {
                            view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        }

                        int newWidth = view.getWidth();
                        int newHeight = view.getHeight();

                        // Dispara o evento com a nova largura e altura
                        CanvasSizeChanged(newWidth, newHeight);
                    }
                });
            }
        });
    }
        
    static class AreaCalculator {
        static double calculateArea(ArrayList<double[]> points) {
            double area = 0.0;
            int n = points.size();
            if (n < 3) return 0.0; // Um polígono válido precisa de ao menos 3 pontos

            for (int i = 0; i < n; i++) {
                double[] current = points.get(i);
                double[] next = points.get((i + 1) % n); // Para garantir que o último ponto se conecte ao primeiro
                area += current[0] * next[1] - next[0] * current[1];
            }
            return Math.abs(area / 2.0);
        }
    }
    
    public class Point {
        private double x;
        private double y;

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public void setLocation(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
            
    public void AddAreaPoint(double x, double y) {
        areaPoints.add(new double[]{x, y});
    }
    
    private void sortPointsForAreaCalculation() {
        if (areaPoints.size() < 3) return; // Precisa de pelo menos 3 pontos para formar um polígono

        // Encontrar o ponto mais baixo (ou o mais à esquerda em caso de empate)
        double[] lowestPoint = areaPoints.get(0);
        for (double[] point : areaPoints) {
            if (point[1] < lowestPoint[1] || (point[1] == lowestPoint[1] && point[0] < lowestPoint[0])) {
                lowestPoint = point;
            }
        }

        // Ordenar os pontos com base no ângulo em relação ao ponto mais baixo
        final double[] finalLowestPoint = lowestPoint;
        Collections.sort(areaPoints, new Comparator<double[]>() {
            @Override
            public int compare(double[] p1, double[] p2) {
                double angle1 = Math.atan2(p1[1] - finalLowestPoint[1], p1[0] - finalLowestPoint[0]);
                double angle2 = Math.atan2(p2[1] - finalLowestPoint[1], p2[0] - finalLowestPoint[0]);
                return Double.compare(angle1, angle2);
            }
        });
    }
}