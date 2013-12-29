package com.paep3nguin.proximityLock;

import java.util.Timer;
import java.util.TimerTask;

import android.app.KeyguardManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

public class LockService extends Service implements SensorEventListener{
	private SensorManager mSensorManager;
	private Sensor mProximity;
	PowerManager powerManager;
	KeyguardManager keyguardManager;
	ComponentName compName;
	DevicePolicyManager deviceManager;
	boolean isScreenOn;
	Timer timer;
	static final int RESULT_ENABLE = 1;
	float proximity;
	WakeLock screenLock;
	ToneGenerator tg;
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onCreate(){
		super.onCreate();
		
		deviceManager = (DevicePolicyManager)getSystemService(  
		          Context.DEVICE_POLICY_SERVICE);

		compName = new ComponentName(this, MyAdmin.class);
		
		powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

		tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME);
	}
	
	public int onStartCommand(Intent intent, int flags, int startId){  
		mSensorManager.registerListener(this,
			    mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),
			    SensorManager.SENSOR_DELAY_NORMAL);
		
		timer = new Timer();
		
		screenLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
		        | PowerManager.ON_AFTER_RELEASE, "MyWakeLock");
		
		super.onStartCommand(intent, flags, startId);
		
		return START_STICKY;
	}
	
	@Override
	public void onDestroy(){
		mSensorManager.unregisterListener(LockService.this);
		super.onDestroy();
	}

	public void startLockTimer(){
		timer.schedule(new TimerTask() {
			   public void run() {
				   if (proximity < 1){
	            		deviceManager.lockNow();
			   		}
			   }
			}, 1000);
	}
	
	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		proximity = event.values[0];
		if (powerManager.isScreenOn() && proximity < 1){
			boolean active = deviceManager.isAdminActive(compName);  
            if (active) {
        		startLockTimer();
            }
		}
		if (!powerManager.isScreenOn() && proximity >= 1){
			boolean active = deviceManager.isAdminActive(compName);  
            if (active) {
				tg.startTone(ToneGenerator.TONE_PROP_BEEP, 1000);
				screenLock.acquire();
			    screenLock.release();
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