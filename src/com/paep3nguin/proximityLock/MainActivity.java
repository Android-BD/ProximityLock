package com.paep3nguin.proximityLock;

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
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener, SensorEventListener{
	private Button button;
	private SensorManager mSensorManager;
	Sensor mProximity;
	static final int RESULT_ENABLE = 1;
	
	ComponentName compName;
	DevicePolicyManager deviceManager;
	TextView textView;
	Window window = this.getWindow();
	boolean isScreenOn;
	boolean active;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		deviceManager = (DevicePolicyManager)getSystemService(  
		          Context.DEVICE_POLICY_SERVICE);
		
		compName = new ComponentName(this, MyAdmin.class);

		setContentView(R.layout.activity_main);
		
		button = (Button) findViewById(R.id.button1);
		button.setOnClickListener(this);
		textView = (TextView) findViewById(R.id.textView1);
		
		active = deviceManager.isAdminActive(compName);
		if (active){
			button.setText("Disable app as device administrator");
		}
		
		ActionBar actionBar = getActionBar();
		actionBar.setDisplayShowHomeEnabled(true);
		
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
		
		mSensorManager.registerListener(this,
			    mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
			    1000000);
		
		//Sets default preference values
		PreferenceManager.setDefaultValues(this, R.xml.prefs, false);
	}

	// Make action bar
	  public boolean onCreateOptionsMenu(Menu menu){
		  MenuInflater inflater = getMenuInflater();
		  inflater.inflate(R.menu.main, menu);
		  return super.onCreateOptionsMenu(menu);
	  }
	  
	  // Detects if service is running
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
			  active = deviceManager.isAdminActive(compName);
			  if (active){
				  startService(new Intent(this, LockService.class));
				  /*textView.setText(Float.toString(mProximity.getPower()));*/
				  Toast.makeText(MainActivity.this, "Service started", Toast.LENGTH_SHORT).show();
			  } else {
				  Toast.makeText(MainActivity.this, "Enable device manager first", Toast.LENGTH_SHORT).show();
			  }
			  break;
		  case R.id.itemServiceStop:
			  stopService(new Intent(this, LockService.class));
			  /*textView.setText(Boolean.toString(isMyServiceRunning()));*/
			  Toast.makeText(MainActivity.this, "Service stopped", Toast.LENGTH_SHORT).show();
			  break;
		  case R.id.itemPreferences:
			  startActivity(new Intent(this, PrefsActivity.class));
			  break;
		  }
		  return true;
	  }

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		if(v == button){
			active = deviceManager.isAdminActive(compName);
			if (!active){
			   Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);  
		            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,  
		                    compName);
		            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,  
		                    "Please click activate to allow this app to lock the screen.");  
		            startActivityForResult(intent, RESULT_ENABLE);
	            button.setText("Disable app as device administrator");
            } else {
  			    stopService(new Intent(this, LockService.class));
  			    Toast.makeText(MainActivity.this, "Service stopped", Toast.LENGTH_SHORT).show();
            	deviceManager.removeActiveAdmin(compName);
	            button.setText("Enable app as device administrator");
            }
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

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub

		/*final double alpha = 0.8;
		
		double[] gravity = {0,0,0};
		
		gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
	    gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
	    gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];
	    
		textView.setText(Double.toString(gravity[0]) + "\n" + Double.toString(gravity[1]) + "\n" + Double.toString(gravity[2]) + "\n" + Float.toString(mProximity.getPower()));*/
		textView.setText(Float.toString(event.values[0]) + "\n" + Float.toString(event.values[1]) + "\n" + Float.toString(event.values[2]) + "\n" + Float.toString(mProximity.getPower()));
		/*Window window = this.getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		textView.setText(Integer.toString(getWindow().getAttributes().flags));*/
	}
}