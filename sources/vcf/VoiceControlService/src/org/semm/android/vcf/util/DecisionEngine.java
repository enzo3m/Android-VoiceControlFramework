package org.semm.android.vcf.util;

import java.util.ArrayList;

import android.util.Log;


/**
 * Esempio di motore decisionale basato sulla distanza di Damerau-Levenshtein,
 * con l'introduzione di un peso associato alle varie tipologie di modifica
 * (inserimento, cancellazione, sostituzione e trasposizione).
 * 
 * @author vincenzo
 *
 */
public class DecisionEngine {
	
	/**
	 * Indica che le stringhe restituite dallo speech recognizer potrebbero
	 * rappresentare più di una stringa di quelle previste, pertanto non si
	 * può prendere alcuna decisione.
	 */
	public static final int MULTIPLE_MATCHES = -1;
	
	/**
	 * Indica che nessuna delle stringhe restituite dallo speech recognizer
	 * rappresenta una delle stringhe di quelle previste. Tipicamente viene
	 * restituito quando si stabilisce una soglia di somiglianza.
	 */
	public static final int NO_MATCH = -2;
	
	/** Peso relativo alla probabilità di inserimento. */
	private double mInsertWeight;
	
	/** Peso relativo alla probabilità di cancellazione. */
	private double mDeletionWeight;
	
	/** Peso relativo alla probabilità di sostituzione. */
	private double mSubstitutionWeight;
	
	/** Peso relativo alla probabilità di trasposizione. */
	private double mTranspositionWeight;
	
	
	/**
	 * Istanzia un nuovo motore decisionale con i pesi predefiniti.
	 */
	public DecisionEngine() {
		this(32, 0.005, 0.005, 0.005, 0.005);
		//mInsertWeight = 0;
		//mDeletionWeight = 0;
		//mSubstitutionWeight = 1;
		//mTranspositionWeight = 0;
	}
	
	/**
	 * Istanzia un nuovo motore decisionale con i parametri specificati.
	 * 
	 * @param k cardinalità dell'alfabeto
	 * @param pi probabilità di inserimento
	 * @param pd probabilità di cancellazione
	 * @param ps probabilità di sostituzione
	 * @param pt probabilità di trasposizione
	 */
	private DecisionEngine(int k, double pi, double pd, double ps, double pt) {
		// http://stackoverflow.com/questions/10389438/how-to-apply-the-levenshtein-distance-to-a-set-of-target-strings
		
		// Probabilità di trovare lo stesso simbolo, supponendo
		// che le probabilità di cancellazione, di sostituzione
		// e di trasposizione siano mutuamente esclusive.
		double pp = 1-pd-ps-pt;
		
		// Peso relativo ad ogni tipo di evento, calcolato in base alla probabilità
		// che si verifichi una determinata modifica e alla cardinalità dell'insieme
		// dei simboli.
		mInsertWeight = -Math.log(pi/pp/k);
		mDeletionWeight = -Math.log(pd/pp);
		mSubstitutionWeight = -Math.log(ps/pp/(k-1));
		mTranspositionWeight = -Math.log(pt/pp);
	}
	
	/**
	 * Calcola la distanza tra le due stringhe specificate.
	 * 
	 * @param s la prima stringa
	 * @param t la seconda stringa
	 * 
	 * @return la distanza tra le due stringhe
	 */
	public double calculateDistance(CharSequence s, CharSequence t) {
		if (s == null || t == null)
			throw new IllegalArgumentException("Le stringhe specificate non possono essere null");
		
		int m = s.length();
		int n = t.length();
		
		double[][] distance = new double[m+1][n+1];
		for (int i=0; i <= m; i++)
			for (int j=0; j <= n; j++)
				distance[i][j] = 0;
		
		for (int i=1; i <= m; i++)
			distance[i][0] = distance[i-1][0] + mDeletionWeight;
		for (int j=1; j <= n; j++)
			distance[0][j] = distance[0][j-1] + mInsertWeight;
		
		for (int i=1; i <= m; i++)
			for (int j=1; j <= n; j++) {
				double replace_cost = (s.charAt(i-1) == t.charAt(j-1) ? 0 : mSubstitutionWeight);
				distance[i][j] = Math.min(Math.min(
						distance[i-1][j] + mDeletionWeight,   // cancellazione
						distance[i][j-1] + mInsertWeight),   // inserimento
						distance[i-1][j-1] + replace_cost);   // sostituzione
				
				if (i > 1 && j > 1 && (s.charAt(i-1) == t.charAt(j-2)) && (s.charAt(i-2) == t.charAt(j-1)))
            		distance[i][j] = Math.min(
            				distance[i][j],
            				distance[i-2][j-2] + mTranspositionWeight  // trasposizione
            				);
			}
		
		return distance[m][n];
	}
	
	/**
	 * Restituisce l'indice di expected relativo alla stringa più probabile.
	 * 
	 * @param expected la lista delle stringhe ammesse
	 * @param recognized la lista delle stringhe da verificare
	 * @param threshold una soglia massima per la distanza
	 *  
	 * @return l'indice relativo alla stringa più probabile oppure
	 *         un codice d'errore (NO_MATCH o MULTIPLE_MATCHES)
	 */
	public int getExpectedString(ArrayList<String> expected, ArrayList<String> recognized, double threshold) {
		Log.i("DecisionEngine", String.format("Pesi[i, d, s, t] = [%f, %f, %f, %f]",
				mInsertWeight, mDeletionWeight, mSubstitutionWeight, mTranspositionWeight));
		
		// distanza minima corrente e riga corrispondente
		double current_min = threshold;
		int row_currmin = NO_MATCH;   // inizializzazione negativa
		
		// # di righe con uguale current_min
		int nor_currmin = 0;
		
		double distance;   // distanza (i,j)-esima
		for (int i=0; i < expected.size(); i++) {
			String expectedStr = expected.get(i);
			for (int j=0; j < recognized.size(); j++) {
				distance = calculateDistance(expectedStr, recognized.get(j));
				
				int comparing = Double.compare(distance, current_min);
				if (comparing < 0) {
					current_min = distance; // nuovo minimo
					row_currmin = i; // riga corrispondente
					nor_currmin = 1;
				}
				else if (comparing == 0 && i > row_currmin) {
					row_currmin = i; // necessario per il conteggio
					nor_currmin++;
				}
			}
		}
		
		return (nor_currmin > 1 ? MULTIPLE_MATCHES : row_currmin);
	}
	
	/**
	 * Restituisce l'indice di expected relativo alla stringa più probabile.
	 * 
	 * @param prefix prefisso relativo alla lista delle stringhe ammesse
	 * @param suffixes suffissi relativi alla lista delle stringhe ammesse
	 * @param recognized la lista delle stringhe da verificare
	 * @param threshold una soglia massima per la distanza
	 * 
	 * @return l'indice relativo alla stringa più probabile oppure
	 *         un codice d'errore (NO_MATCH o MULTIPLE_MATCHES)
	 */
	public int getExpectedString(String prefix, ArrayList<String> suffixes, ArrayList<String> recognized, double threshold) {
		// P.S.: non ottimizzato!
		ArrayList<String> expected = new ArrayList<String>(suffixes.size());
		for (String str : suffixes)
			expected.add(prefix + str);
		return getExpectedString(expected, recognized, threshold);
	}
	
	/**
	 * Restituisce l'indice di expected relativo alla stringa più probabile.
	 * 
	 * @param expected la lista delle stringhe ammesse
	 * @param recognized la lista delle stringhe da verificare
	 *  
	 * @return l'indice relativo alla stringa più probabile oppure
	 *         un codice d'errore (NO_MATCH o MULTIPLE_MATCHES)
	 */
	public int getExpectedString(ArrayList<String> expected, ArrayList<String> recognized) {
		return this.getExpectedString(expected, recognized, Double.MAX_VALUE);
	}
	
	/**
	 * Restituisce l'indice di expected relativo alla stringa più probabile.
	 * 
	 * @param prefix prefisso relativo alla lista delle stringhe ammesse
	 * @param suffixes suffissi relativi alla lista delle stringhe ammesse
	 * @param recognized la lista delle stringhe da verificare
	 * 
	 * @return l'indice relativo alla stringa più probabile oppure
	 *         un codice d'errore (NO_MATCH o MULTIPLE_MATCHES)
	 */
	public int getExpectedString(String prefix, ArrayList<String> suffixes, ArrayList<String> recognized) {
		// P.S.: non ottimizzato!
		ArrayList<String> expected = new ArrayList<String>(suffixes.size());
		for (String str : suffixes)
			expected.add(prefix + str);
		return getExpectedString(expected, recognized);
	}
	
}
