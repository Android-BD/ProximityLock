package com.paep3nguin.proximityLock;

import com.paep3nguin.proximityLock.LockService.prefValues;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
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

public class LockService extends Service implements SensorEventListener, OnSystemUiVisibilityChangeListener, OnSharedPreferenceChangeListener{
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
	OnSharedPreferenceChangeListener listener;
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
	boolean unlockBeep;
	boolean faceDownLock;
	boolean rotateLock;
	int lockDelay;
	int unlockDelay;
	int lockMethod;
	int gravityRate;
	int upsideDownLockAngle;
	int tableLockAngle;
	
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
		
		//Initializes wake lock
		screenLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
		        | PowerManager.ON_AFTER_RELEASE, "ScreenOnLock");
		partialLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PartialLock");

		//Makes tone generator to play beeps
		tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME);

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
		upsideDownLockAngle = Integer.parseInt(sharedPref.getString("upsideDownLockAngle", "50"));
		tableLockAngle = Integer.parseInt(sharedPref.getString("tableLockAngle", "70"));
		rotateLock = sharedPref.getBoolean("rotateLock", true);
		faceDownLock = sharedPref.getBoolean("faceDownLock", true);
		unlockBeep = sharedPref.getBoolean("unlockBeep", false);

		lastyAcceleration = 0;
		lastzAcceleration = 0;
		float pi = 3.14159265359f;
		yLockThreshold = (float) (9.8*Math.sin(upsideDownLockAngle/180*pi));
		zLockThreshold = (float) (9.8*Math.sin(tableLockAngle/180*pi));

		sharedPref.registerOnSharedPreferenceChangeListener(this);
		
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
			break;
		}
		
		super.onStartCommand(intent, flags, startId);
		return START_STICKY;
	}
	
	@Override
	public void onDestroy(){
		//Unregisters listeners
		mSensorManager.unregisterListener(this, mProximity);
		mSensorManager.unregisterListener(this, mGravity);
    	sharedPref.unregisterOnSharedPreferenceChangeListener(this);
    	
    	//Removes any remaining handler events  
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

	@Override
	public void onSystemUiVisibilityChange(int arg0) {
		//Implement!!!
	}
	
	public enum prefValues{
		lockMethod, lockDelay, unlockDelay, gravityRate, upsideDownLockAngle, tableLockAngle, rotateLock, faceDownLock, unlockBeep;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {

		float pi = 3.14159265359f;
		
		//Get values for preferences
		switch (prefValues.valueOf(key)){
		case lockMethod:
			lockMethod = Integer.parseInt(sharedPref.getString("lockMethod","1"));

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
				break;
			}
			break;
		case lockDelay:
			lockDelay = Integer.parseInt(sharedPref.getString("lockDelay", "1000"));
			break;
		case unlockDelay:
			unlockDelay = Integer.parseInt(sharedPref.getString("unlockDelay", "1000"));
			break;
		case gravityRate:
			gravityRate = Integer.parseInt(sharedPref.getString("gravityRate", "500"));
			break;
		case upsideDownLockAngle:
			upsideDownLockAngle = Integer.parseInt(sharedPref.getString("upsideDownLockAngle", "50"));
			yLockThreshold = (float) (9.8*Math.sin(upsideDownLockAngle/180*pi));
			break;
		case tableLockAngle:
			tableLockAngle = Integer.parseInt(sharedPref.getString("tableLockAngle", "70"));
			zLockThreshold = (float) (9.8*Math.sin(tableLockAngle/180*pi));
			break;
		case rotateLock:
			rotateLock = sharedPref.getBoolean("rotateLock", true);
			break;
		case faceDownLock:
			faceDownLock = sharedPref.getBoolean("faceDownLock", true);
			break;
		case unlockBeep:
			unlockBeep = sharedPref.getBoolean("unlockBeep", false);
			break;
		}
	}
	
	//Sounds a beep if the proper preference is checked
	public void beep(){
		if(unlockBeep == true){
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
				    if(partialLock.isHeld())
				    	partialLock.release();
		   }
	};
	
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
		        		timerHandler.postDelayed(lockTimer, lockDelay);
					}
					if (proximity >= 1){
		            	timerHandler.removeCallbacks(lockTimer);
					}
				}
				if (!powerManager.isScreenOn()){
					if (proximity >= 1){
						partialLock.acquire();
		            	timerHandler.postDelayed(unlockTimer, unlockDelay);
					}
					if (proximity < 1){
		            	timerHandler.removeCallbacks(unlockTimer);
					    if(partialLock.isHeld())
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
		        		timerHandler.postDelayed(lockTimer, lockDelay);
					}
					if (yAcceleration >= -yLockThreshold && lastyAcceleration < -yLockThreshold){
		            	timerHandler.removeCallbacks(lockTimer);
					}
				}
				if (!powerManager.isScreenOn()){
					if (yAcceleration >= -yLockThreshold && lastyAcceleration < -yLockThreshold){
						partialLock.acquire();
		            	timerHandler.postDelayed(unlockTimer, unlockDelay);
					}
					if(yAcceleration < -yLockThreshold && lastyAcceleration >= -yLockThreshold){
		            	timerHandler.removeCallbacks(unlockTimer);
					    if(partialLock.isHeld())
					    	partialLock.release();
					}
				}
				if (faceDownLock){
					//Locks if screen is face down
					if (powerManager.isScreenOn()){
						if (zAcceleration < -zLockThreshold && lastzAcceleration >= -zLockThreshold){
			        		timerHandler.postDelayed(lockTimer, lockDelay);
						}
						if (zAcceleration >= -zLockThreshold && lastzAcceleration < -zLockThreshold){
			            	timerHandler.removeCallbacks(lockTimer);
						}
					}
					if (!powerManager.isScreenOn()){
						if (zAcceleration >= -zLockThreshold && lastzAcceleration < -zLockThreshold){
							partialLock.acquire();
			            	timerHandler.postDelayed(unlockTimer, unlockDelay);
						}
						if(zAcceleration < -zLockThreshold && lastzAcceleration >= -zLockThreshold){
			            	timerHandler.removeCallbacks(unlockTimer);
						    if(partialLock.isHeld())
						    	partialLock.release();
						}
					}
				}
				lastyAcceleration = event.values[1];
				lastzAcceleration = event.values[2];
			}
			break;
		case 3:
			break;
		}
	}

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		//Unnecessary
	}
}