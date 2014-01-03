package com.paep3nguin.proximityLock;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.OrientationEventListener;
import android.view.View.OnSystemUiVisibilityChangeListener;

public class LockService extends Service implements SensorEventListener, OnSystemUiVisibilityChangeListener{
	private SensorManager mSensorManager;
	private Sensor mProximity;
	boolean isProximityRegistered;
	private Sensor mGravity;
	OrientationEventListener oListener;
	PowerManager powerManager;
	KeyguardManager keyguardManager;
	ComponentName compName;
	DevicePolicyManager deviceManager;
	boolean isScreenOn;
	static final int RESULT_ENABLE = 1;
	float proximity;
	float lastyAcceleration;
	WakeLock screenLock;
	ToneGenerator tg;
	SharedPreferences sharedPref;
	private Handler timerHandler = new Handler();
	//Preference values
	boolean beepPref;
	boolean rotateLock;
	int lockDelay;
	int unlockDelay;
	int lockMethod;
	int gravityRate;
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onCreate(){
		super.onCreate();
		
		//Registers device manager
		deviceManager = (DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);
		compName = new ComponentName(this, MyAdmin.class);
		
		//Registers power manager
		powerManager = (PowerManager)getSystemService(POWER_SERVICE);

		//Makes tone generator to play beeps
		tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME);
		
		//Initializes wakelock
		screenLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
		        | PowerManager.ON_AFTER_RELEASE, "MyWakeLock");

		//Gets shared preferences
		this.sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		
		//Registers sensor manager and sensors
		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
	}
	
	public int onStartCommand(Intent intent, int flags, int startId){
		
		//Get values for preferences
		lockMethod = Integer.parseInt(sharedPref.getString("lockMethod","1"));
		lockDelay = Integer.parseInt(sharedPref.getString("lockDelay", "1000"));
		unlockDelay = Integer.parseInt(sharedPref.getString("unlockDelay", "1000"));
		gravityRate = Integer.parseInt(sharedPref.getString("gravityRate", "500"));
		rotateLock = sharedPref.getBoolean("rotateLock", true);
		beepPref = sharedPref.getBoolean("unlockBeep", false);
		
		//Registers listeners depending on selected lock method
		switch (lockMethod){
		case 0: case 1:
			//Registers proximity sensor listener
			isProximityRegistered = false;
			isProximityRegistered = mSensorManager.registerListener(this,
				    mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),
				    SensorManager.SENSOR_DELAY_NORMAL);
			break;
		case 2: case 3:
			//Registers gravity sensor listener
			mSensorManager.registerListener(this,
				    mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
				    gravityRate * 1000);

			lastyAcceleration = 0;
			break;
		}
		
		super.onStartCommand(intent, flags, startId);
		return START_STICKY;
	}
	
	@Override
	public void onDestroy(){
		mSensorManager.unregisterListener(this, mProximity);
		mSensorManager.unregisterListener(this, mGravity);
    	timerHandler.removeCallbacks(null);
		super.onDestroy();
	}
	
	@Override
	public void onTaskRemoved(Intent rootIntent) {
	    Intent restartService = new Intent(getApplicationContext(),
	            this.getClass());
	    restartService.setPackage(getPackageName());
	    PendingIntent restartServicePI = PendingIntent.getService(
	            getApplicationContext(), 1, restartService,
	            PendingIntent.FLAG_ONE_SHOT);
	    AlarmManager alarmService = (AlarmManager)getApplicationContext().getSystemService(Context.ALARM_SERVICE);
	    alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() +10000, restartServicePI);

	}

	//Sounds a beep if the proper preference is checked
	public void beep(){
		if(beepPref == true){
			tg.startTone(ToneGenerator.TONE_PROP_BEEP, 1000);			
		}
	}
	
	private Runnable lockTimer = new Runnable(){
		@Override
		public void run() {
         		beep();
         		deviceManager.lockNow();
        		
         		if (lockMethod == 1){
	        		//Registers gravity sensor listener
					mSensorManager.registerListener(LockService.this,
						    mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
						    gravityRate * 1000);
				}
		   }
	};
	
	private Runnable unlockTimer = new Runnable(){
		@Override
		public void run() {
					beep();
					screenLock.acquire();
				    screenLock.release();
				    
				    if (lockMethod == 1){
				    	mSensorManager.unregisterListener(LockService.this, mGravity);
					}
		   }
	};
	
	@Override
	public void onSystemUiVisibilityChange(int arg0) {
		//Implement!!!
	}
	
	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		//Unnecessary
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		switch (lockMethod){
		case 1:
			if (event.sensor.getType() == Sensor.TYPE_GRAVITY){
				float yAcceleration = event.values[1];
				if (yAcceleration >= -5){
					//Registers proximity sensor listener
					if (isProximityRegistered == false){
						isProximityRegistered = mSensorManager.registerListener(this,
						    mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),
						    SensorManager.SENSOR_DELAY_NORMAL);
					}
				}
				if (yAcceleration < -5){
	         		if (isProximityRegistered == true){
	         			mSensorManager.unregisterListener(this, mProximity);
	         			isProximityRegistered = false;
	         		}
				}
			}
		case 0:
			if (event.sensor.getType() == Sensor.TYPE_PROXIMITY){
				proximity = event.values[0];
				if (powerManager.isScreenOn() && proximity < 1){
	        		timerHandler.postDelayed(lockTimer, lockDelay);
				}
				if (powerManager.isScreenOn() && proximity >= 1){
	            	timerHandler.removeCallbacks(lockTimer);
				}
				if (!powerManager.isScreenOn() && proximity >= 1){
	            	timerHandler.postDelayed(unlockTimer, unlockDelay);
				}
				if (!powerManager.isScreenOn() && proximity < 1){
	            	timerHandler.removeCallbacks(unlockTimer);
				}
			}
			break;
		case 2:
			if (event.sensor.getType() == Sensor.TYPE_GRAVITY){
				float yAcceleration = event.values[1];
				if (powerManager.isScreenOn() && yAcceleration < -5 && lastyAcceleration >= -5){
	        		timerHandler.postDelayed(lockTimer, lockDelay);
				}
				if (powerManager.isScreenOn() && yAcceleration >= -5 && lastyAcceleration < -5){
	            	timerHandler.removeCallbacks(lockTimer);
				}
				if (!powerManager.isScreenOn() && yAcceleration >= -5 && lastyAcceleration < -5){
	            	timerHandler.postDelayed(unlockTimer, unlockDelay);
				}
				if (!powerManager.isScreenOn() && yAcceleration < -5 && lastyAcceleration >= -5){
	            	timerHandler.removeCallbacks(unlockTimer);
				}
				lastyAcceleration = event.values[1];
			}
			break;
		}
	}
}