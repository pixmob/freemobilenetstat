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

import static org.pixmob.freemobile.netstat.Constants.DEBUG;
import static org.pixmob.freemobile.netstat.Constants.TAG;

import org.pixmob.freemobile.netstat.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * Custom component showing a chart with battery levels since the device was
 * turned on.
 * @author Pixmob
 */
public class BatteryLevelChart extends View {
    private final Paint levelBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint levelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int axisThickness = 3;
    private final int axisUnitThickness = 10;
    private final int lineStrokeWidth = 6;
    private int[] levels;
    private long[] timestamps;
    
    public BatteryLevelChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        final Resources r = getResources();
        axisPaint.setColor(r.getColor(R.color.chart_axis_color));
        axisPaint.setStyle(Paint.Style.FILL);
        
        levelBorderPaint.setColor(r.getColor(R.color.chart_line_color));
        levelBorderPaint.setStyle(Paint.Style.STROKE);
        levelBorderPaint.setStrokeWidth(lineStrokeWidth);
        
        levelBgPaint.setColor(r.getColor(R.color.chart_fg_color));
        levelBgPaint.setStyle(Paint.Style.FILL);
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
        
        final int points = levels.length;
        final int w = getWidth();
        final int h = getHeight();
        final float xScale = w / (float) points;
        final float yScale = h / 10f;
        
        if (DEBUG) {
            Log.d(TAG, "Drawing battery levels: w=" + w + "; h=" + h);
        }
        
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
