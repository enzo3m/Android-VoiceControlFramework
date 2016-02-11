package org.semm.android.vcf.app;

import java.util.List;

import org.semm.android.vcf.IService;
import org.semm.android.vcf.IServiceCallback;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * La classe <code>ControlledActivity</code> garantisce la trasparenza della comunicazione
 * e si occupa di gestire lo scambio di dati tra l'applicazione e il servizio di controllo
 * vocale.
 * <p>
 * Per realizzare un'applicazione in grado di comunicare col servizio di controllo vocale,
 * cioè che sia VCF-capable, è sufficiente che lo sviluppatore derivi tutte le activity che
 * la compongono dalla <code>ControlledActivity</code>, effettuando l'override dei metodi
 * astratti in essa definiti.
 * 
 * @author vincenzo
 * @see android.app.Activity
 */
public abstract class ControlledActivity extends Activity {
	
	/* Tag per logging. */
	private static final String LOG_TAG = ControlledActivity.class.getSimpleName();
	
	/* Il nome del servizio con cui collegarsi. */
	private final String VCS_CLASSNAME = "org.semm.android.vcf.VoiceControlService";
	
	/* Identifica l'interfaccia del servizio con cui collegarsi. */
	private final Intent VCS_INTENT = new Intent("org.semm.android.vcf.IService");
	
	/* Interfaccia per comunicare col servizio. */
	private IService service = null;
	
	/* Segnala se l'applicazione si è collegata col servizio. */
	private boolean bound = false;
	
	/* Connessione col servizio di controllo vocale. */
	private ServiceConnection svcConn = new ServiceConnection() {
		public void onServiceConnected(ComponentName component, IBinder binder) {
	    	service = IService.Stub.asInterface(binder);
	    	Log.i(LOG_TAG, "onServiceConnected(): " + component.getClassName());
	    	Log.i(LOG_TAG, "onServiceConnected(): binder " + binder);
	    	doRegisterCallback();
	    }
	    public void onServiceDisconnected(ComponentName component) {
	    	service = null;
	    	Log.i(LOG_TAG, "onServiceDisconnected(): " + component.getClassName());
	    }
	};
    
    /* Callback per ricevere comandi dal servizio di controllo vocale. */
    private IServiceCallback.Stub callback = new IServiceCallback.Stub() {
    	@Override
		public void listening(boolean active, int error) throws RemoteException {
			Log.v(LOG_TAG, String.format("Riconoscitore vocale %s (errore: %d)", (active ? "attivo" : "non attivo"), error));
			onListening(active, error);
		}
		@Override
		public void execute(Bundle params) throws RemoteException {
			Log.v(LOG_TAG, String.format("Nuovo comando ricevuto (dati: %s)", params));
			onExecute(params);
		}
		@Override
		public void close(Bundle params) throws RemoteException {
			Log.v(LOG_TAG, String.format("Richiesta di chiusura applicazione (parametri: %s)", params));
			onClose(params);
		}
	};
	
	/**
	 * Chiamato quando l'activity viene creata per la prima volta, effettua il bind al servizio
	 * di controllo vocale. Le sottoclassi che effettuano l'override di questo metodo, devono
	 * invocarlo come prima istruzione all'interno della versione overridden.
	 * 
	 * @see android.app.Activity
	 */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(LOG_TAG, "onCreate()");
        
        if (this.isVoiceControlServiceRunning()) {
        	Log.v(LOG_TAG, "onCreate(): collegamento al servizio di controllo vocale");
            bound = getApplicationContext().bindService(
            		VCS_INTENT, svcConn, Context.BIND_AUTO_CREATE);
            if (!bound) { Log.e(LOG_TAG, "Impossibile collegarsi al servizio di controllo vocale"); }
        }
        else {
        	Log.e(LOG_TAG, "onCreate(): servizio di controllo vocale inattivo");
        	bound = false;
        }
    }
    
    /**
     * Rilascia tutte le risorse precedentemente allocate prima che l'activity venga distrutta,
     * inviando una notifica al servizio di controllo vocale ed effettuando l'unbind da esso.
     * Poiché tali operazioni devono essere eseguite per ultime, le sottoclassi che effettuano
     * l'override di questo metodo, devono chiamarlo come ultima istruzione all'interno della
     * versione overridden.
     * 
     * @see android.app.Activity
     */
    @Override
    protected void onDestroy() {
    	Log.i(LOG_TAG, "onDestroy()");
    	
    	if (service != null) {
    		try {   // conferma la chiusura dell'applicazione
    			Log.v(LOG_TAG, "onDestroy(): invio conferma di chiusura al servizio di controllo vocale");
    			service.confirmClosing();
    		} catch (RemoteException e) {
    			Log.e(LOG_TAG, "onDestroy(): RemoteException " + e.getMessage());
    		}
    	}
    	
    	if (bound) {   // interrompe la comunicazione col servizio
    		Log.v(LOG_TAG, "onDestroy(): interrompo la comunicazione col servizio di controllo vocale");
    		getApplicationContext().unbindService(svcConn);
    		bound = false;
    	}
    	
    	super.onDestroy();   // completa il rilascio delle risorse
    }
    
    /**
     * Verifica se il servizio di controllo vocale è in esecuzione.
     * 
     * @return true se il servizio di controllo vocale è in esecuzione
     */
    private boolean isVoiceControlServiceRunning() {
    	final ActivityManager activityManager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);
        
        for (ActivityManager.RunningServiceInfo runningServiceInfo : services) {
            if (runningServiceInfo.service.getClassName().equals(VCS_CLASSNAME)){
                return true;
            }
        }
        return false;
    }
    
	/** Registra la callback per ricevere comandi da parte del servizio di controllo vocale. */
	private void doRegisterCallback() {
		Log.i(LOG_TAG, "Registrazione callback IServiceCallback");
		try {
			service.registerCallback(callback);
		} catch (RemoteException e) {
			Log.e(LOG_TAG, "Registrazione callback fallita: RemoteException " + e.getMessage());
		}
	}

	/**
	 * Il servizio di controllo vocale notifica l'applicazione sul cambiamento di stato relativo
	 * allo speech recognizer, specificando se è attivo e l'eventuale codice d'errore restituito.
	 * Generalmente viene utilizzato dall'applicazione per fornire un feedback visuale all'utente
	 * in modo che l'utente sappia quando lo speech recongizer è attivo o meno.
	 * <p>
	 * Per l'implementazione di questo metodo, bisogna tenere presente che le eventuali istruzioni
	 * che interagiscono con la UI devono essere eseguite nel main thread. A tal fine, per esempio,
	 * è possibile usare il metodo {@link android.os.Handler#post(Runnable) post} oppure anche
	 * il metodo {@link android.app.Activity#runOnUiThread(Runnable) runOnUiThread}.
	 * 
	 * @param active <code>true</code> se lo speech recognizer è attivo,
	 *               <code>false</code> altrimenti
	 * @param error l'eventuale errore restituito dallo speech recognizer
	 * @see android.app.Activity#runOnUiThread(Runnable) runOnUiThread
	 * @see android.os.Handler#post(Runnable) post
	 */
	protected abstract void onListening(boolean active, int error); /*{
		Log.i(LOG_TAG, String.format("onListening(active: %b, error: %d)", active, error));
	}*/

	/**
	 * Il servizio di controllo vocale ha richiesto l'esecuzione di un comando.
	 * Una tipica implementazione di questo metodo prevede l'analisi dell'argomento specificato
	 * al fine di individuare il comando inviato, quindi la sua esecuzione. Dopo che il comando
	 * è stato eseguito, bisogna chiamare il metodo {@link #setExecuteResult(boolean, String)}
	 * per inviarne l'esito al servizio di controllo vocale.
	 * <p>
	 * Per l'implementazione di questo metodo, bisogna tenere presente che le eventuali istruzioni
	 * che interagiscono con la UI devono essere eseguite nel main thread. A tal fine, per esempio,
	 * è possibile usare il metodo {@link android.os.Handler#post(Runnable) post} oppure anche
	 * il metodo {@link android.app.Activity#runOnUiThread(Runnable) runOnUiThread}.
	 * 
	 * @param params parametri impostati dal servizio di controllo vocale.
	 * @see #setExecuteResult(boolean, String)
	 * @see android.app.Activity#runOnUiThread(Runnable) runOnUiThread
	 * @see android.os.Handler#post(Runnable) post
	 */
	protected abstract void onExecute(Bundle params); /*{
		Log.i(LOG_TAG, String.format("onExecute(params: %s)", params));
	}*/

	/**
	 * Il servizio di controllo vocale ha richiesto la chiusura dell'applicazione.
	 * Tutte le sottoclassi che implementano questo metodo hanno il dovere di terminare correttamente
	 * l'activity principale dell'applicazione, pertanto come ultima istruzione all'interno di questo
	 * metodo devono chiamare esplicitamente il metodo {@link android.app.Activity#finish() finish}
	 * dell'activity principale.
	 * <p>
	 * Per l'implementazione di questo metodo, bisogna tenere presente che le eventuali istruzioni
	 * che interagiscono con la UI devono essere eseguite nel main thread. A tal fine, per esempio,
	 * è possibile usare il metodo {@link android.os.Handler#post(Runnable) post} oppure anche
	 * il metodo {@link android.app.Activity#runOnUiThread(Runnable) runOnUiThread}.
	 * 
	 * @param params parametri impostati dal servizio di controllo vocale. Riservati per usi futuri.
	 * @see android.app.Activity#runOnUiThread(Runnable) runOnUiThread
	 * @see android.os.Handler#post(Runnable) post
	 */
	protected abstract void onClose(Bundle params); /*{
		Log.i(LOG_TAG, String.format("onClose(params: %s)", params));
		finish();
	}*/
	
	/**
	 * Permette di notificare il servizio di controllo vocale in merito al completamento dell'esecuzione
	 * di un comando precedentemente inviato. Deve essere invocato non appena l'esecuzione di un comando
	 * è terminata, indipendentemente dall'esito.
	 *  
	 * @param success l'esito relativo all'esecuzione del comando (<code>true</code> se positivo,
	 * <code>false</code> altrimenti).
	 * @param utterance il messaggio che deve essere pronunziato dal servizio di controllo vocale
	 * per confermare l'esecuzione del comando o per segnalare un eventuale errore. Se la stringa
	 * specificata è vuota oppure <code>null</code>, allora viene ignorata e ne viene pronunziata
	 * una predefinita.
	 */
	protected final void setExecuteResult(boolean success, String utterance) {
		Log.i(LOG_TAG, String.format("setExecuteResult(success: %b, utterance: %s)", success, utterance));
		try {
			service.resultFromExecute(success, utterance);
		} catch (RemoteException e) {
			Log.e(LOG_TAG, "setExecuteResult(): RemoteException " + e.getMessage());
		}
	}

}
