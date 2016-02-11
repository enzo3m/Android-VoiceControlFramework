package org.semm.android.vcf.temp;

import java.util.ArrayList;

/**
 * Una semplice classe per simulare risultati multipli di uno speech recongizer.
 * 
 * @author vincenzo
 *
 */
public final class Voce {
	
	public static ArrayList<String> getSimulatedVoice01() {
		ArrayList<String> result = new ArrayList<String>();
		result.add("android avvia demo");
		result.add("android a via demo");
		result.add("android avvia demoo");
		return result;
	}
	
	public static ArrayList<String> getSimulatedVoice02() {
		ArrayList<String> result = new ArrayList<String>();
		result.add("android esegui primo comando");
		result.add("android segui primoo comando");
		result.add("android esegui prima domanda");
		return result;
	}
	
	public static ArrayList<String> getSimulatedVoice03() {
		ArrayList<String> result = new ArrayList<String>();
		result.add("android segui mondo comando");
		result.add("android esegui secondo comando");
		return result;
	}
	
	public static ArrayList<String> getSimulatedVoice04() {
		ArrayList<String> result = new ArrayList<String>();
		result.add("android esegui terzo andando");		
		result.add("android segui marzo comando");		
		result.add("android esegui terzo comando");		
		result.add("android e segui terzo comando");
		return result;
	}
	
	public static ArrayList<String> getSimulatedVoice05() {
		ArrayList<String> result = new ArrayList<String>();
		result.add("android chi di applicazione");		
		result.add("android chiudi applica");		
		result.add("android chiudii appli cauzione");		
		result.add("android chiudi applicazione");
		return result;
	}
	
	
}
