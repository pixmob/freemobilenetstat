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

import java.lang.ref.WeakReference;

import org.pixmob.freemobile.netstat.Event;
import org.pixmob.freemobile.netstat.MobileOperator;
import org.pixmob.freemobile.netstat.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Custom component showing battery levels with a chart.
 * @author Pixmob
 */
public class BatteryChart extends View {
    private WeakReference<Bitmap> cacheRef;
    private int freeMobileColor;
    private int orangeColor;
    private Paint mobileOperatorPaint;
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
    
    private void doDraw(Canvas canvas) {
        final int margins = 8;
        final int w = getWidth() - margins * 2;
        final int h = getHeight() - margins * 2;
        final float x0Graph = yTextPaint.measureText("100") + 2;
        final float y0Graph = margins;
        final float y0Mob = 0;
        
        // Draw every lines with a single call to Canvas#drawLines, for better
        // performance.
        final float[] lines = new float[11 * 4];
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
            if (bgPaint.getColor() == bgColor1) {
                bgPaint.setColor(bgColor2);
            } else {
                bgPaint.setColor(bgColor1);
            }
            final float y = h - yFactor * i + y0Graph;
            canvas.drawRect(x0Graph, y, w, y + yBand, bgPaint);
            if (i != 100) {
                canvas.drawText(String.valueOf(i), x0Text, y - yAscent2,
                    yTextPaint);
                
                lines[lineIdx++] = x0Graph;
                lines[lineIdx++] = y;
                lines[lineIdx++] = w;
                lines[lineIdx++] = y;
            }
        }
        
        if (events != null && events.length > 1) {
            final long t0 = events[0].timestamp;
            final float xFactor = (w - x0Graph)
                    / (events[events.length - 1].timestamp - t0);
            
            final int eventCount = events.length;
            final int lastEventIdx = eventCount - 1;
            
            final Path batteryPath = new Path();
            batteryPath.moveTo(x0Graph, h);
            batteryPath.incReserve(eventCount + 2);
            float lastY = 0;
            
            for (int i = 0; i < eventCount; ++i) {
                final Event e = events[i];
                float x = (e.timestamp - t0) * xFactor + x0Graph;
                if (x < x0Graph) {
                    continue;
                }
                final float y = h - e.batteryLevel * yFactor + y0Graph;
                
                if (i != 0) {
                    batteryPath.lineTo(x, y);
                } else {
                    batteryPath.moveTo(x, y);
                }
                lastY = y;
                
                final MobileOperator mobOp = MobileOperator
                        .fromString(e.mobileOperator);
                if (mobOp != null) {
                    if (MobileOperator.FREE_MOBILE.equals(mobOp)) {
                        mobileOperatorPaint.setColor(freeMobileColor);
                    } else if (MobileOperator.ORANGE.equals(mobOp)) {
                        mobileOperatorPaint.setColor(orangeColor);
                    }
                    
                    float x2 = w;
                    if (i != lastEventIdx) {
                        final Event e1 = events[i + 1];
                        x2 = (e1.timestamp - t0) * xFactor;
                        if (x2 < x0Graph) {
                            x2 = x;
                        }
                        if (x2 > w) {
                            x2 = w;
                        }
                    }
                    canvas.drawLine(x, y0Mob, x2, y0Mob, mobileOperatorPaint);
                }
            }
            
            batteryPath.lineTo(w, lastY);
            batteryPath.lineTo(w, h);
            batteryPath.lineTo(x0Graph, h);
            canvas.drawPath(batteryPath, batteryLevelPaint);
        }
        
        // Draw axes.
        canvas.drawLines(lines, yBarPaint);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Lazy initialize paint properties, once.
        if (batteryLevelPaint == null) {
            batteryLevelPaint = new Paint();
            batteryLevelPaint.setStyle(Paint.Style.FILL);
            
            final int c1 = getResources()
                    .getColor(R.color.battery_level_color1);
            final int c2 = getResources()
                    .getColor(R.color.battery_level_color2);
            batteryLevelPaint.setShader(new LinearGradient(0, 0, 0,
                    getHeight(), c1, c2, Shader.TileMode.CLAMP));
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
        if (mobileOperatorPaint == null) {
            mobileOperatorPaint = new Paint();
            mobileOperatorPaint.setStrokeWidth(12);
            
            orangeColor = getResources()
                    .getColor(R.color.orange_network_color1);
            freeMobileColor = getResources().getColor(
                R.color.free_mobile_network_color1);
        }
        
        // Get the previously drawn image.
        Bitmap cache = null;
        if (cacheRef != null) {
            cache = cacheRef.get();
        }
        
        // No image is available in the cache: render a new one.
        if (cache == null) {
            cache = Bitmap.createBitmap(getWidth(), getHeight(),
                Bitmap.Config.ARGB_8888);
            final Canvas c = new Canvas(cache);
            doDraw(c);
            cacheRef = new WeakReference<Bitmap>(cache);
        }
        
        canvas.drawBitmap(cache, 0, 0, null);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec));
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cacheRef = null;
    }
    
    public void setData(Event[] events) {
        this.events = events;
        cacheRef = null;
    }
}
