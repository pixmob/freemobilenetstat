/*
 * Copyright (C) 2012 Pixmob (http://github.com/pixmob)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pixmob.freemobile.netstat.ui;

import org.pixmob.freemobile.netstat.R;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Custom component showing mobile network use with a pie chart.
 * 
 * @author Pixmob
 */
public class MobileNetworkChart extends View {
    private final RectF circleBounds = new RectF();
    private float startAngle = 270;
    private float orange2GAngle;
    private float orange3GAngle;
    private float freeMobile3GAngle;
    private float freeMobile4GAngle;
    private Paint arcBorderPaint;
    private Paint arcFillPaint;
    private Paint orange2GPaint;
    private Paint orange3GPaint;
    private Paint freeMobile3GPaint;
    private Paint freeMobile4GPaint;
    private Paint unknownPaint;
    private int circleColor = -1;

    public MobileNetworkChart(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setData(75, 10, 10, 80);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        initPaints();

        final int w = getWidth();
        final int h = getHeight();
        final int radius = Math.min(w, h) - 4;
        final int startX = (w - radius) / 2;
        final int startY = (h - radius) / 2;
        circleBounds.set(startX, startY, startX + radius, startY + radius);

        drawChart(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    }

    public void setData(int percentOnOrange, int percentOnFreeMobile, int percentOnOrange2G, int percentOnFreeMobile3G) {
    	final float orangeAngle = percentToAngle(normalizePercent(percentOnOrange));
    	final float freeMobileAngle = percentToAngle(normalizePercent(percentOnFreeMobile));
        orange2GAngle = orangeAngle * (normalizePercent(percentOnOrange2G) / 100.f);
        orange3GAngle = orangeAngle - orange2GAngle;
        freeMobile3GAngle = freeMobileAngle * (normalizePercent(percentOnFreeMobile3G) / 100.f);
        freeMobile4GAngle = freeMobileAngle - freeMobile3GAngle;
        startAngle = freeMobileAngle / 2.f;
    }

    private static float percentToAngle(int p) {
        return p * 360 / 100f;
    }

    private static int normalizePercent(int p) {
        return Math.min(100, Math.max(0, p));
    }
    
    private Paint initGradientPaint(int color1, int color2) {
    	Paint paint = new Paint();
    	paint.setAntiAlias(true);
    	paint.setStyle(Paint.Style.FILL);

        paint.setShader(new LinearGradient(0, 0, 0, getHeight(), color1, color2, Shader.TileMode.CLAMP));
        
        return paint;
    }
    
    private void initPaints() {
        if (orange2GPaint == null) {
            final int c1 = getResources().getColor(R.color.orange_2G_network_color1);
            final int c2 = getResources().getColor(R.color.orange_2G_network_color2);
            orange2GPaint = initGradientPaint(c1, c2);
        }
        if (orange3GPaint == null) {
            final int c1 = getResources().getColor(R.color.orange_3G_network_color1);
            final int c2 = getResources().getColor(R.color.orange_3G_network_color2);
            orange3GPaint = initGradientPaint(c1, c2);
        }
        if (freeMobile3GPaint == null) {
            final int c1 = getResources().getColor(R.color.free_mobile_3G_network_color1);
            final int c2 = getResources().getColor(R.color.free_mobile_3G_network_color2);
            freeMobile3GPaint = initGradientPaint(c1, c2);
        }
        if (freeMobile4GPaint == null) {
            final int c1 = getResources().getColor(R.color.free_mobile_4G_network_color1);
            final int c2 = getResources().getColor(R.color.free_mobile_4G_network_color2);
            freeMobile4GPaint = initGradientPaint(c1, c2);
        }
        if (unknownPaint == null) {
            final int c = getResources().getColor(R.color.unknown_mobile_network_color);
            unknownPaint = initGradientPaint(c, c);
        }
        
        if (circleColor == -1) {
            circleColor = getResources().getColor(R.color.pie_border_color);
        }

        if (arcBorderPaint == null) {
            arcBorderPaint = new Paint();
            arcBorderPaint.setAntiAlias(true);
            arcBorderPaint.setStyle(Paint.Style.STROKE);
            arcBorderPaint.setColor(circleColor);
            arcBorderPaint.setStrokeWidth(2);
        }
        if (arcFillPaint == null) {
            arcFillPaint = new Paint();
            arcFillPaint.setAntiAlias(true);
            arcFillPaint.setStyle(Paint.Style.FILL);
        }
    }
    
    private void drawChart(Canvas canvas) {
    	float currentAngle = startAngle;
        if (orange2GAngle > 0) {
            canvas.drawArc(circleBounds, currentAngle, orange2GAngle, true, arcBorderPaint);
            canvas.drawArc(circleBounds, currentAngle, orange2GAngle, true, orange2GPaint);
        	currentAngle += orange2GAngle;
        }
        if (orange3GAngle > 0) {
            canvas.drawArc(circleBounds, currentAngle, orange3GAngle, true, arcBorderPaint);
            canvas.drawArc(circleBounds, currentAngle, orange3GAngle, true, orange3GPaint);
        	currentAngle += orange3GAngle;
        }
        if (freeMobile3GAngle > 0) {
            canvas.drawArc(circleBounds, currentAngle, freeMobile3GAngle, true, arcBorderPaint);
            canvas.drawArc(circleBounds, currentAngle, freeMobile3GAngle, true, freeMobile3GPaint);
        	currentAngle += freeMobile3GAngle;
        }
        if (freeMobile4GAngle > 0) {
            canvas.drawArc(circleBounds, currentAngle, freeMobile4GAngle, true, arcBorderPaint);
            canvas.drawArc(circleBounds, currentAngle, freeMobile4GAngle, true, freeMobile4GPaint);
            currentAngle += freeMobile4GAngle;
        }

        final float unknownAngle = 360 - currentAngle + startAngle;
        if (unknownAngle > 0) {
            canvas.drawArc(circleBounds, currentAngle, unknownAngle, true, arcBorderPaint);
            canvas.drawArc(circleBounds, currentAngle, unknownAngle, true, unknownPaint);
        }
    }
}
