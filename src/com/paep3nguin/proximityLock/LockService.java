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
	//Sensors
	private SensorManager mSensorManager;
	private Sensor mProximity;
	boolean isProximityRegistered;
	private Sensor mGravity;
	float proximity;
	float lastyAcceleration;
	float lastzAcceleration;
	float yLockThreshold;
	float zLockThreshold;
	
	OrientationEventListener oListener;
	PowerManager powerManager;
	DevicePolicyManager deviceManager;
	ComponentName compName;
	KeyguardManager keyguardManager;
	boolean isScreenOn;
	static final int RESULT_ENABLE = 1;
	WakeLock screenLock;
	WakeLock partialLock;
	ToneGenerator tg;
	private Handler timerHandler = new Handler();
	
	//Preference values
	SharedPreferences sharedPref;
	boolean beepPref;
	boolean faceDownLock;
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
		
		//Initializes wake lock
		screenLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
		        | PowerManager.ON_AFTER_RELEASE, "MyWakeLock");
		partialLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");

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
		faceDownLock = sharedPref.getBoolean("faceDownLock", true);
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
			lastzAcceleration = 0;
			yLockThreshold = 5;
			zLockThreshold = 8;
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
         		partialLock.release();
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
	         		partialLock.release();
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
	public void onSensorChanged(SensorEvent event){
		if(rotateLock){
			int width = getApplicationContext().getResources().getDisplayMetrics().widthPixels;
			int height = getApplicationContext().getResources().getDisplayMetrics().heightPixels;
			if(height < width){return;}
		}
		switch (lockMethod){
		case 1:
			if (event.sensor.getType() == Sensor.TYPE_GRAVITY){
				float yAcceleration = event.values[1];
				float zAcceleration = event.values[2];
				if (yAcceleration >= -yLockThreshold){
					//Registers proximity sensor listener
					if (isProximityRegistered == false){
						isProximityRegistered = mSensorManager.registerListener(this,
						    mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),
						    SensorManager.SENSOR_DELAY_NORMAL);
					}
				}
				if (yAcceleration < -yLockThreshold){
	         		if (isProximityRegistered == true){
	         			mSensorManager.unregisterListener(this, mProximity);
	         			isProximityRegistered = false;
	         		}
				}
				if(faceDownLock){
					if (zAcceleration >= -zLockThreshold){
						//Registers proximity sensor listener
						if (isProximityRegistered == false){
							isProximityRegistered = mSensorManager.registerListener(this,
							    mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),
							    SensorManager.SENSOR_DELAY_NORMAL);
						}
					}
					if (zAcceleration < -zLockThreshold){
		         		if (isProximityRegistered == true){
		         			mSensorManager.unregisterListener(this, mProximity);
		         			isProximityRegistered = false;
		         		}
					}
				}
			}
		case 0:
			if (event.sensor.getType() == Sensor.TYPE_PROXIMITY){
				proximity = event.values[0];
				if (powerManager.isScreenOn()){
					if (proximity < 1){
						partialLock.acquire();
		        		timerHandler.postDelayed(lockTimer, lockDelay);
					}
					if (proximity >= 1){
		            	timerHandler.removeCallbacks(lockTimer);
		            	partialLock.release();
					}
				}
				if (!powerManager.isScreenOn()){
					if (proximity >= 1){
						partialLock.acquire();
		            	timerHandler.postDelayed(unlockTimer, unlockDelay);
					}
					if (proximity < 1){
		            	timerHandler.removeCallbacks(unlockTimer);
		            	partialLock.release();
					}
				}
			}
			break;
		case 2:
			if (event.sensor.getType() == Sensor.TYPE_GRAVITY){
				float yAcceleration = event.values[1];
				float zAcceleration = event.values[2];
				//Locks if screen is upside down
				if (powerManager.isScreenOn()){
					if (yAcceleration < -yLockThreshold && lastyAcceleration >= -yLockThreshold){
						partialLock.acquire();
		        		timerHandler.postDelayed(lockTimer, lockDelay);
					}
					if (yAcceleration >= -yLockThreshold && lastyAcceleration < -yLockThreshold){
		            	timerHandler.removeCallbacks(lockTimer);
		            	partialLock.release();
					}
				}
				if (!powerManager.isScreenOn()){
					if (yAcceleration >= -yLockThreshold && lastyAcceleration < -yLockThreshold){
						partialLock.acquire();
		            	timerHandler.postDelayed(unlockTimer, unlockDelay);
					}
					if(yAcceleration < -yLockThreshold && lastyAcceleration >= -yLockThreshold){
		            	timerHandler.removeCallbacks(unlockTimer);
		            	partialLock.release();
					}
				}
				if (faceDownLock){
					//Locks if screen is face down
					if (powerManager.isScreenOn()){
						if (zAcceleration < -zLockThreshold && lastzAcceleration >= -zLockThreshold){
							partialLock.acquire();
			        		timerHandler.postDelayed(lockTimer, lockDelay);
						}
						if (zAcceleration >= -zLockThreshold && lastzAcceleration < -zLockThreshold){
			            	timerHandler.removeCallbacks(lockTimer);
			            	partialLock.release();
						}
					}
					if (!powerManager.isScreenOn()){
						if (zAcceleration >= -zLockThreshold && lastzAcceleration < -zLockThreshold){
							partialLock.acquire();
			            	timerHandler.postDelayed(unlockTimer, unlockDelay);
						}
						if(zAcceleration < -zLockThreshold && lastzAcceleration >= -zLockThreshold){
			            	timerHandler.removeCallbacks(unlockTimer);
			            	partialLock.release();
						}
					}
				}
				lastyAcceleration = event.values[1];
				lastzAcceleration = event.values[2];
			}
			break;
		}
	}
}