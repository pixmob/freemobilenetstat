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
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * Custom component showing state history.
 * @author Pixmob
 */
public class StateChart extends View {
    private Paint statePaint;
    private Paint textPaint;
    private boolean[] states;
    private long[] timestamps;
    private int nameRes;
    
    public StateChart(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        
        nameRes = attrs.getAttributeResourceValue(null, "name",
            R.string.app_name);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (states == null || timestamps == null
                || states.length != timestamps.length) {
            return;
        }
        
        if (statePaint == null) {
            statePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            statePaint.setStyle(Paint.Style.FILL);
            statePaint.setColor(getResources().getColor(
                R.color.chart_fg_color_start));
        }
        if (textPaint == null) {
            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG
                    | Paint.SUBPIXEL_TEXT_FLAG);
            textPaint.setColor(getResources()
                    .getColor(R.color.chart_text_color));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(getResources().getInteger(
                R.integer.chart_text_size));
        }
        
        if (timestamps.length > 0) {
            final int w = getWidth();
            final int h = getHeight();
            final long t0 = timestamps[0];
            final float xScale = w
                    / (float) (timestamps[timestamps.length - 1] - t0);
            
            final int lastStateIdx = states.length - 1;
            for (int i = 0; i < lastStateIdx; ++i) {
                final boolean state = states[i];
                if (state) {
                    final float x1 = xScale * (timestamps[i] - t0);
                    final float y1 = 0;
                    final float x2 = xScale * (timestamps[i + 1] - t0);
                    final float y2 = h;
                    canvas.drawRect(x1, y1, x2, y2, statePaint);
                }
            }
            if (lastStateIdx != 0) {
                final boolean state = states[lastStateIdx];
                if (state) {
                    float x1 = xScale * (timestamps[lastStateIdx] - t0);
                    if (x1 >= w) {
                        x1 = w - 1;
                    }
                    final float y1 = 0;
                    final float x2 = w;
                    final float y2 = h;
                    canvas.drawRect(x1, y1, x2, y2, statePaint);
                }
            }
        }
        
        final int textMargin = 10;
        canvas.drawText(getResources().getString(nameRes), textMargin,
            -textPaint.ascent(), textPaint);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec));
    }
    
    public void setData(long[] timestamps, boolean[] states) {
        this.timestamps = timestamps;
        this.states = states;
    }
}
