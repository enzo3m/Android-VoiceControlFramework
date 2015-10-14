package org.semm.android.vcf.temp;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import android.os.Bundle;

/**
 * Una semplice classe per simulare le preferenze.
 * 
 * @author vincenzo
 *
 */
public class Preferenze {
	
	private HashMap<String, HashMap<String, Bundle>> mApps;
	
	public Preferenze() {
		mApps = new HashMap<String, HashMap<String, Bundle>>();
		
		HashMap<String, Bundle> demoApp = new HashMap<String, Bundle>();
		Bundle demoAppB1 = new Bundle(); demoAppB1.putString("command01", "comando 01");  // inviati dall'app
		Bundle demoAppB2 = new Bundle(); demoAppB2.putString("command02", "comando 02");  // al servizio
		demoApp.put("esegui primo comando", demoAppB1);     // impostati nell'app e inviati
		demoApp.put("esegui secondo comando", demoAppB2);   // dall'app al servizio
		mApps.put("demo", demoApp);
		// devono essere salvati con le shared preferences
	}
	
	public Set<String> getStringSet(String pref, Set<String> def) {
	    if (pref.equalsIgnoreCase("AvailableApps")) {
		    return new HashSet<String>(Arrays.asList(new String[]{"demo", "biglietteria"}));
		}
		return def;
	}
		
	public String getPackageName(String appId, String def) {
		if (appId.equalsIgnoreCase("demo"))
			return "org.semm.android.ctrlappdemo";
		return def;
	}
	
	public Set<String> getAppCommands(String appId) {
		return mApps.get(appId).keySet();
	}
	
	public Bundle getAppCommand(String appId, String command) {
		return mApps.get(appId).get(command);
	}
}
