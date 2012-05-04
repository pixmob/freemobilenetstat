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
 * @author Pixmob
 */
public class MobileNetworkChart extends View {
    private float startAngle = 270;
    private float orangeAngle;
    private float freeMobileAngle;
    private Paint arcBorderPaint;
    private Paint arcFillPaint;
    private Paint orangePaint;
    private Paint freeMobilePaint;
    private Paint unknownPaint;
    private int circleColor = -1;
    
    public MobileNetworkChart(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setData(75, 10);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Lazy initialize paint properties, once.
        if (orangePaint == null) {
            orangePaint = new Paint();
            orangePaint.setAntiAlias(true);
            orangePaint.setStyle(Paint.Style.FILL);
            
            final int c1 = getResources().getColor(
                R.color.orange_network_color1);
            final int c2 = getResources().getColor(
                R.color.orange_network_color2);
            orangePaint.setShader(new LinearGradient(0, 0, 0, getHeight(), c1,
                    c2, Shader.TileMode.CLAMP));
        }
        if (freeMobilePaint == null) {
            freeMobilePaint = new Paint();
            freeMobilePaint.setAntiAlias(true);
            freeMobilePaint.setStyle(Paint.Style.FILL);
            
            final int c1 = getResources().getColor(
                R.color.free_mobile_network_color1);
            final int c2 = getResources().getColor(
                R.color.free_mobile_network_color2);
            freeMobilePaint.setShader(new LinearGradient(0, 0, 0, getHeight(),
                    c1, c2, Shader.TileMode.CLAMP));
        }
        if (unknownPaint == null) {
            unknownPaint = new Paint();
            unknownPaint.setAntiAlias(true);
            unknownPaint.setStyle(Paint.Style.FILL);
            unknownPaint.setColor(getResources().getColor(
                R.color.unknown_mobile_network_color));
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
        
        final int w = getWidth();
        final int h = getHeight();
        final int radius = Math.min(w, h) - 4;
        final int startX = (w - radius) / 2;
        final int startY = (h - radius) / 2;
        final RectF circleBounds = new RectF(startX, startY, startX + radius,
                startY + radius);
        
        if (orangeAngle > 0) {
            canvas.drawArc(circleBounds, startAngle, orangeAngle, true,
                arcBorderPaint);
            canvas.drawArc(circleBounds, startAngle, orangeAngle, true,
                orangePaint);
        }
        if (freeMobileAngle > 0) {
            canvas.drawArc(circleBounds, startAngle + orangeAngle,
                freeMobileAngle, true, arcBorderPaint);
            canvas.drawArc(circleBounds, startAngle + orangeAngle,
                freeMobileAngle, true, freeMobilePaint);
        }
        
        final float unknownAngle = 360 - orangeAngle - freeMobileAngle;
        if (unknownAngle > 0) {
            canvas.drawArc(circleBounds, startAngle + orangeAngle
                    + freeMobileAngle, unknownAngle, true, arcBorderPaint);
            canvas.drawArc(circleBounds, startAngle + orangeAngle
                    + freeMobileAngle, unknownAngle, true, unknownPaint);
        }
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec));
    }
    
    public void setData(int percentOnOrange, int percentOnFreeMobile) {
        orangeAngle = percentToAngle(normalizePercent(percentOnOrange));
        freeMobileAngle = percentToAngle(normalizePercent(percentOnFreeMobile));
        startAngle = freeMobileAngle / 2f;
    }
    
    private static float percentToAngle(int p) {
        return p * 360 / 100f;
    }
    
    private static int normalizePercent(int p) {
        if (p < 0) {
            return 0;
        }
        if (p > 100) {
            return 100;
        }
        return p;
    }
}
