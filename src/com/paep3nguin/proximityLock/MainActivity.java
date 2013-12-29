package com.paep3nguin.proximityLock;

import android.os.Bundle;
import android.os.PowerManager;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener/*, SensorEventListener*/{
	private Button button;
	private SensorManager mSensorManager;
	private Sensor mProximity;
	static final int RESULT_ENABLE = 1;
	
	ComponentName compName;
	DevicePolicyManager deviceManager;
	TextView textView;
	Window window = this.getWindow();
	boolean isScreenOn;
	PowerManager powerManager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		deviceManager = (DevicePolicyManager)getSystemService(  
		          Context.DEVICE_POLICY_SERVICE);
		
		compName = new ComponentName(this, MyAdmin.class);

		setContentView(R.layout.activity_main);
		
		button = (Button) findViewById(R.id.button1);
		button.setOnClickListener(this);
		textView = (TextView) findViewById(R.id.textView1);
		
		ActionBar actionBar = getActionBar();
		actionBar.setDisplayShowHomeEnabled(true);
		
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		Sensor mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		
		/*mSensorManager.registerListener(this,
			    mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),
			    SensorManager.SENSOR_DELAY_NORMAL);*/
		
		powerManager = (PowerManager) getSystemService(POWER_SERVICE);
	}

	// Make action bar
	  public boolean onCreateOptionsMenu(Menu menu){
		  MenuInflater inflater = getMenuInflater();
		  inflater.inflate(R.menu.main, menu);
		  return super.onCreateOptionsMenu(menu);
	  }
	  
	  private boolean isMyServiceRunning() {
		    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
		        if (LockService.class.getName().equals(service.service.getClassName())) {
		            return true;
		        }
		    }
		    return false;
		}
	  
	//Event handling for menu bar clicks
	  @Override
	  public boolean onOptionsItemSelected(MenuItem item){
		  switch (item.getItemId()){
		  case R.id.itemServiceStart:
			  startService(new Intent(this, LockService.class));
			  textView.setText(Boolean.toString(isMyServiceRunning()));
			  Toast.makeText(MainActivity.this, "Service started", Toast.LENGTH_SHORT).show();
			  break;
		  case R.id.itemServiceStop:
			  stopService(new Intent(this, LockService.class));
			  textView.setText(Boolean.toString(isMyServiceRunning()));
			  Toast.makeText(MainActivity.this, "Service stopped", Toast.LENGTH_SHORT).show();
			  break;
		  }
		  return true;
	  }

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		if(v == button){  
		   Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);  
	            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,  
	                    compName);
	            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,  
	                    "Additional text explaining why this needs to be added.");  
	            startActivityForResult(intent, RESULT_ENABLE);  
		  }
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {  
        switch (requestCode) {  
            case RESULT_ENABLE:  
                if (resultCode == Activity.RESULT_OK) {  
                    Log.i("DeviceAdminSample", "Admin enabled!");  
                } else {  
                    Log.i("DeviceAdminSample", "Admin enable FAILED!");  
                }  
                return;  
        }  
        super.onActivityResult(requestCode, resultCode, data);  
    }

	/*@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		float proximity = event.values[0];
		if (powerManager.isScreenOn() && proximity < 1){
			boolean active = deviceManager.isAdminActive(compName);  
            if (active) {
                deviceManager.lockNow();
            }
		}
		if (!powerManager.isScreenOn() && proximity >= 1){
			boolean active = deviceManager.isAdminActive(compName);  
            if (active) {
                window.addFlags(LayoutParams.FLAG_DISMISS_KEYGUARD);
                window.addFlags(LayoutParams.FLAG_SHOW_WHEN_LOCKED);
                window.addFlags(LayoutParams.FLAG_TURN_SCREEN_ON);
            } 
		}
		if (!powerManager.isScreenOn() && proximity >= 2){
			boolean active = deviceManager.isAdminActive(compName);  
            if (active) {
                window.addFlags(LayoutParams.FLAG_DISMISS_KEYGUARD);
                window.addFlags(LayoutParams.FLAG_SHOW_WHEN_LOCKED);
                window.addFlags(LayoutParams.FLAG_TURN_SCREEN_ON);
            } 
		}
	    textView.setText(Boolean.toString(powerManager.isScreenOn()));
	}*/
}