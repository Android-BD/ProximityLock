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
import android.view.View.OnSystemUiVisibilityChangeListener;

public class LockService extends Service implements SensorEventListener, OnSystemUiVisibilityChangeListener{
	private SensorManager mSensorManager;
	private Sensor mProximity;
	boolean isProximityRegistered;
	private Sensor mGravity;
	PowerManager powerManager;
	KeyguardManager keyguardManager;
	ComponentName compName;
	DevicePolicyManager deviceManager;
	boolean isScreenOn;
	static final int RESULT_ENABLE = 1;
	float proximity;
	WakeLock screenLock;
	ToneGenerator tg;
	SharedPreferences sharedPref;
	private Handler timerHandler = new Handler();
	//Preference values
	boolean beepPref;
	int lockDelay;
	int unlockDelay;
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onCreate(){
		super.onCreate();
		
		deviceManager = (DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);

		compName = new ComponentName(this, MyAdmin.class);
		
		powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		
		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

		this.sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		
		//Registers proximity sensor listener
		isProximityRegistered = false;
		isProximityRegistered = mSensorManager.registerListener(this,
			    mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),
			    SensorManager.SENSOR_DELAY_NORMAL);

		//Makes tone generator to play beeps
		tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME);
		
		//Get settings
		beepPref = sharedPref.getBoolean("unlockBeep", false);
		lockDelay = Integer.parseInt(sharedPref.getString("lockDelay", "1000"));
		unlockDelay = Integer.parseInt(sharedPref.getString("unlockDelay", "1000"));
	}
	
	public int onStartCommand(Intent intent, int flags, int startId){
		
		screenLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
		        | PowerManager.ON_AFTER_RELEASE, "MyWakeLock");
		
		super.onStartCommand(intent, flags, startId);
		
		return START_STICKY;
	}
	
	@Override
	public void onDestroy(){
		mSensorManager.unregisterListener(this, mProximity);
		mSensorManager.unregisterListener(this, mGravity);
		super.onDestroy();
	}
	
	@Override
	public void onTaskRemoved(Intent rootIntent) {
	    // TODO Auto-generated method stub
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
        		
        		//Registers gravity sensor listener
				mSensorManager.registerListener(LockService.this,
					    mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
					    1000000);
		   }
	};
	
	private Runnable unlockTimer = new Runnable(){
		@Override
		public void run() {
					beep();
					screenLock.acquire();
				    screenLock.release();
					mSensorManager.unregisterListener(LockService.this, mGravity);
		   }
	};
	
	@Override
	public void onSystemUiVisibilityChange(int arg0) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		if (event.sensor.getType() == Sensor.TYPE_PROXIMITY){
			proximity = event.values[0];
			if (powerManager.isScreenOn() && proximity < 1){
				boolean active = deviceManager.isAdminActive(compName);  
	            if (active) {
	        		timerHandler.postDelayed(lockTimer, lockDelay);
	            }
			}
			if (powerManager.isScreenOn() && proximity >= 1){
				boolean active = deviceManager.isAdminActive(compName);  
	            if (active) {
	            	timerHandler.removeCallbacks(lockTimer);
	            }
			}
			if (!powerManager.isScreenOn() && proximity >= 1){
				boolean active = deviceManager.isAdminActive(compName);  
	            if (active) {
	            	timerHandler.postDelayed(unlockTimer, unlockDelay);
	            }
			}
			if (!powerManager.isScreenOn() && proximity < 1){
				boolean active = deviceManager.isAdminActive(compName);  
	            if (active) {
	            	timerHandler.removeCallbacks(unlockTimer);
	            }
			}
		}
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
		/*if (!powerManager.isScreenOn() && proximity >= 2){
			boolean active = deviceManager.isAdminActive(compName);  
            if (active) {
                window.addFlags(LayoutParams.FLAG_DISMISS_KEYGUARD);
                window.addFlags(LayoutParams.FLAG_SHOW_WHEN_LOCKED);
                window.addFlags(LayoutParams.FLAG_TURN_SCREEN_ON);
            } 
		}*/
	}
}