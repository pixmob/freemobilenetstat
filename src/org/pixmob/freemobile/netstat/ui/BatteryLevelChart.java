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
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Custom component showing a chart with battery levels.
 * @author Pixmob
 */
public class BatteryLevelChart extends View {
    private Paint levelBorderPaint;
    private Paint levelBgPaint;
    private Paint axisPaint;
    private final int axisThickness = 3;
    private final int axisUnitThickness = 10;
    private final int lineStrokeWidth = 8;
    private int[] levels;
    private long[] timestamps;
    
    public BatteryLevelChart(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }
    
    public void setData(long[] timestamps, int[] levels) {
        this.timestamps = timestamps;
        this.levels = levels;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (levels == null || levels.length == 0 || timestamps == null
                || timestamps.length == 0 || levels.length != timestamps.length) {
            return;
        }
        
        if (axisPaint == null) {
            axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            axisPaint.setColor(getResources()
                    .getColor(R.color.chart_axis_color));
            axisPaint.setStyle(Paint.Style.FILL);
        }
        if (levelBorderPaint == null) {
            levelBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            levelBorderPaint.setColor(getResources().getColor(
                R.color.chart_line_color));
            levelBorderPaint.setStyle(Paint.Style.STROKE);
            levelBorderPaint.setStrokeWidth(lineStrokeWidth);
        }
        if (levelBgPaint == null) {
            levelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG
                    | Paint.FILTER_BITMAP_FLAG);
            levelBgPaint.setColor(getResources().getColor(
                R.color.chart_fg_color_end));
            levelBgPaint.setStyle(Paint.Style.FILL);
            levelBgPaint.setDither(true);
        }
        
        final int points = levels.length;
        final int w = getWidth();
        final int h = getHeight();
        final float xScale = w / (float) points;
        final float yScale = h / 10f;
        
        // Plot data.
        final Path levelPath = new Path();
        final int lastPointIdx = points - 1;
        for (int i = 0; i < points; ++i) {
            final int level = levels[i];
            final float x = i == lastPointIdx ? w : i * xScale;
            float y = h - level * yScale / 10f;
            if (y <= 0) {
                y = lineStrokeWidth / 2;
            }
            if (i != 0) {
                levelPath.lineTo(x, y);
            } else {
                levelPath.moveTo(x, y);
            }
        }
        canvas.drawPath(levelPath, levelBorderPaint);
        levelPath.lineTo(w, h);
        levelPath.lineTo(0, h);
        levelPath.close();
        levelBgPaint.setShader(new LinearGradient(0, h, 0, 0, getResources()
                .getColor(R.color.chart_fg_color_start), getResources()
                .getColor(R.color.chart_fg_color_end), Shader.TileMode.CLAMP));
        canvas.drawPath(levelPath, levelBgPaint);
        
        // Draw Y axis.
        canvas.drawRect(0, 0, axisThickness, h, axisPaint);
        // Draw X axis.
        canvas.drawRect(0, h - axisThickness, w, h, axisPaint);
        
        // Draw Y axis units.
        for (int i = 0; i < 10; ++i) {
            final float y = i * yScale;
            canvas.drawRect(0, y, axisUnitThickness, y - axisThickness,
                axisPaint);
        }
        canvas.drawRect(0, 0, axisUnitThickness, axisThickness, axisPaint);
        
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec));
    }
}
