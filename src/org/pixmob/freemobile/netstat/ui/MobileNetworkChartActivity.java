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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Application chart screen.
 * @author gilbsgilbs
 */
public class MobileNetworkChartActivity extends Activity{
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.mobile_network_chart_activity);
	    
	    final MobileNetworkChart mobileNetworkChart = (MobileNetworkChart) findViewById(R.id.mobile_network_chart);
	    final TextView onOrange2GnetworkTextView = (TextView) findViewById(R.id.on_orange_2G_network);
	    final TextView onOrange3GnetworkTextView = (TextView) findViewById(R.id.on_orange_3G_network);
	    final TextView onFreeMobile3GnetworkTextView = (TextView) findViewById(R.id.on_free_mobile_3G_network);
	    final TextView onFreeMobile4GnetworkTextView = (TextView) findViewById(R.id.on_free_mobile_4G_network);

	    Intent intent = getIntent();
	    
	    final int onOrangeNetwork = intent.getIntExtra("on_orange_network", 0);
	    final int onFreeMobileNetwork = intent.getIntExtra("on_free_mobile_network", 100 - onOrangeNetwork);
	    final int onOrange2GNetwork = intent.getIntExtra("on_orange_2G_network", 0);
	    final int onOrange3GNetwork = intent.getIntExtra("on_orange_3G_network", 100 - onOrange2GNetwork);
	    final int onFreeMobile3GNetwork = intent.getIntExtra("on_free_mobile_3G_network", 0);
	    final int onFreeMobile4GNetwork = intent.getIntExtra("on_free_mobile_4G_network", 100 - onFreeMobile3GNetwork);

	    mobileNetworkChart.setData(onOrangeNetwork, onFreeMobileNetwork, onOrange2GNetwork, onFreeMobile3GNetwork);
	    onOrange2GnetworkTextView.setText(onOrange2GNetwork * onOrangeNetwork / 100 + "%");
	    onOrange3GnetworkTextView.setText(onOrange3GNetwork * onOrangeNetwork / 100 + "%");
	    onFreeMobile3GnetworkTextView.setText(onFreeMobile3GNetwork * onFreeMobileNetwork / 100 + "%");
	    onFreeMobile4GnetworkTextView.setText(onFreeMobile4GNetwork * onFreeMobileNetwork / 100 + "%");
	}
}
