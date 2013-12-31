package com.paep3nguin.proximityLock;
import com.paep3nguin.proximityLock.R;

import android.os.Bundle;
import android.preference.PreferenceFragment;


public class PrefsFragment extends PreferenceFragment{
	
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.prefs);
	}
}
