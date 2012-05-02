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

import org.pixmob.freemobile.netstat.Event;
import org.pixmob.freemobile.netstat.R;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Custom component showing battery levels with a chart.
 * @author Pixmob
 */
public class BatteryChart extends View {
    private int bgColor1;
    private int bgColor2;
    private Paint bgPaint;
    private Paint yBarPaint;
    private Paint yTextPaint;
    private Paint batteryLevelPaint;
    private Event[] events;
    
    public BatteryChart(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (batteryLevelPaint == null) {
            batteryLevelPaint = new Paint();
            batteryLevelPaint.setStyle(Paint.Style.FILL);
            batteryLevelPaint.setColor(getResources().getColor(
                R.color.battery_level_color));
        }
        if (bgPaint == null) {
            bgColor1 = getResources().getColor(R.color.battery_bg_color1);
            bgColor2 = getResources().getColor(R.color.battery_bg_color2);
            bgPaint = new Paint();
            bgPaint.setStyle(Paint.Style.FILL);
        }
        if (yTextPaint == null) {
            yTextPaint = new Paint();
            yTextPaint.setAntiAlias(true);
            yTextPaint.setSubpixelText(true);
            yTextPaint.setColor(getResources().getColor(
                R.color.battery_y_text_color));
            yTextPaint.setTextSize(getResources().getDimension(
                R.dimen.battery_y_text_size));
            yTextPaint.setTextAlign(Paint.Align.RIGHT);
            yTextPaint.setStrokeWidth(2);
        }
        if (yBarPaint == null) {
            yBarPaint = new Paint();
            yBarPaint.setColor(getResources().getColor(
                R.color.battery_y_bar_color));
            yBarPaint.setStrokeWidth(2);
        }
        
        final int margins = 8;
        final int w = getWidth() - margins * 2;
        final int h = getHeight() - margins * 2;
        final float x0Graph = yTextPaint.measureText("100") + 2;
        
        final float[] lines = new float[12 * 4];
        int lineIdx = 0;
        lines[lineIdx++] = x0Graph;
        lines[lineIdx++] = 0;
        lines[lineIdx++] = x0Graph;
        lines[lineIdx++] = h;
        lines[lineIdx++] = x0Graph;
        lines[lineIdx++] = h;
        lines[lineIdx++] = w;
        lines[lineIdx++] = h;
        
        // Draw Y units.
        final float x0Text = x0Graph - 5;
        final float yFactor = h / 100f;
        final int bandSize = 10;
        final float yBand = yFactor * bandSize;
        final float yAscent2 = yTextPaint.ascent() / 2;
        for (int i = bandSize; i <= 100; i += bandSize) {
            if (bgPaint.getColor() == bgColor2) {
                bgPaint.setColor(bgColor1);
            } else {
                bgPaint.setColor(bgColor2);
            }
            final float y = h - yFactor * i;
            canvas.drawRect(x0Graph, y, w, y + yBand, bgPaint);
            if (i != 100) {
                canvas.drawText(String.valueOf(i), x0Text, y - yAscent2,
                    yTextPaint);
            }
            canvas.drawLine(x0Graph, y, w, y, yTextPaint);
            
            lines[lineIdx++] = x0Graph;
            lines[lineIdx++] = y;
            lines[lineIdx++] = w;
            lines[lineIdx++] = y;
        }
        
        if (events != null && events.length > 1) {
            final long t0 = events[0].timestamp;
            final float xFactor = (w - x0Graph)
                    / (events[events.length - 1].timestamp - t0);
            
            final Path p = new Path();
            p.moveTo(x0Graph, h);
            float lastY = 0;
            
            final int eventCount = events.length;
            p.incReserve(eventCount + 2);
            
            for (int i = 0; i < eventCount; ++i) {
                final Event e = events[i];
                final float x = (e.timestamp - t0) * xFactor + x0Graph;
                final float y = h - e.batteryLevel * yFactor;
                
                if (i != 0) {
                    p.lineTo(x, y);
                } else {
                    p.moveTo(x, y);
                }
                
                lastY = y;
            }
            
            p.lineTo(w, lastY);
            p.lineTo(w, h);
            p.lineTo(x0Graph, h);
            canvas.drawPath(p, batteryLevelPaint);
        }
        
        // Draw axes.
        canvas.drawLines(lines, yBarPaint);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec));
    }
    
    public void setData(Event[] events) {
        this.events = events;
    }
}
