package org.semm.android.vcf;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import org.semm.android.vcf.IService;
import org.semm.android.vcf.IServiceCallback;
import org.semm.android.vcf.temp.Preferenze;
import org.semm.android.vcf.temp.Voce;
import org.semm.android.vcf.util.DecisionEngine;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.widget.Toast;

/**
 * La classe <code>VoiceControlService</code> implementa un servizio di controllo vocale,
 * ovvero è capace di ricevere e portare a termine i comandi vocali necessari per avviare
 * e controllare le applicazioni basate sul VoiceControlFramework.
 * 
 * I comandi vocali per l'avvio e la chiusura di un'applicazione sono definiti all'interno
 * delle string resources di questo stesso servizio e pertanto possono essere localizzate.
 * 
 * Sviluppi futuri: i comandi vocali per pilotare l'applicazione potrebbero essere definiti
 * come string resources dell'applicazione stessa, per cui il servizio di controllo vocale
 * potrebbe via via richiederli, e inoltre anche questi sarebbero localizzati.
 * 
 * @author vincenzo
 * @see android.app.Service
 * 
 */
public class VoiceControlService extends Service implements OnInitListener {
	
	/* Tag per i messaggi di log. */
	private final static String LOG_TAG = VoiceControlService.class.getSimpleName();
	
	/* Motore di sinteti vocale. */
	private TextToSpeech mTTS;
	
	/* Motore decisionale post riconoscimento vocale. */
	private DecisionEngine mDE = new DecisionEngine();
	
	/*
	 * I possibili stati del servizio sono: NOTHING (nessuna app avviata), LAUNCHING_APP
	 * (app in fase di avvio), APP_RUNNING (app in esecuzione), EXECUTING_CMD (esecuzione
	 * comando in corso), CLOSING_APP (in fase di chiusura).
	 */
	private enum ApplicationStatus { NOTHING, LAUNCHING_APP, APP_RUNNING, EXECUTING_CMD, CLOSING_APP };
	
	/* Stato corrente dell'applicazione. */
	private ApplicationStatus mCurrentAppStatus = ApplicationStatus.NOTHING;
	
	// Nome dell'applicazione che correntemente è attiva,
    // cioè dallo stato di avvio a quello di chiusura.
	private String mCurrentApp = null;
	
	// ==================================================
	// TODO: simulano riconoscitore vocale e preferenze.
	private Timer timer;
	private Preferenze prefs = new Preferenze();
	// ==================================================
	
	// Callback per inviare comandi all'applicazione.
	private IServiceCallback mApplicationCallback = null;
		
	/* Consente il collegamento da parte delle applicazioni. */
	private final IService.Stub binder = new IService.Stub() {
		@Override
		public void registerCallback(IServiceCallback callback)
				throws RemoteException {
			registerCallbackImpl(callback);
		}
		@Override
		public void resultFromExecute(boolean success, String message)
				throws RemoteException {
			resultFromExecuteImpl(success, message);
		}
		@Override
		public void confirmClosing()
				throws RemoteException {
			confirmClosingImpl();
		}
	};
	
	/**
	 * Permette di avviare manualmente il servizio.
	 * 
	 * @see android.app.Service#onStartCommand(Intent, int, int) onStartCommand
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(LOG_TAG, "onStartCommand()");
		return START_STICKY;
	}
	
	/**
	 * Permette ad un'applicazione di collegarsi con questo servizio.
	 * 
	 * @see android.app.Service#onBind(Intent) onBind
	 */
	@Override
	public IBinder onBind(Intent intent) {
		Log.i(LOG_TAG, String.format("onBind(intent: %s)", intent));
		return binder;
	}
	
	/**
	 * Interrompe la comunicazione con questo servizio.
	 * 
	 * @see android.app.Service#onUnbind(Intent) onUnbind
	 */
	@Override
	public boolean onUnbind(Intent intent) {
		Log.i(LOG_TAG, String.format("onUnbind(intent: %s)", intent));
		mApplicationCallback = null;
		return super.onUnbind(intent);
	}
	
	/**
	 * Chiamato quando il servizio viene creato per la prima volta.
	 * 
	 * @see android.app.Service#onCreate() onCreate
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(LOG_TAG, "onCreate()");
		
		/*
		 * Per semplicità, si suppone che il device abbia i file di risorsa
		 * installati correttamente, quindi non si effettua alcuna verifica
		 * in tal senso.
		 */
		mTTS = new TextToSpeech(this, this);
	}
	
	/**
	 * Permette di sapere quando l'inizializzazione del motore TTS è stata completata.
	 * Viene invocato automaticamente ed al suo interno è possibile configurare il TTS.
	 * Se l'inizializzazione non va a buon fine, il servizio di controllo vocale viene
	 * terminato.
	 * 
	 * @see android.speech.tts.TextToSpeech.OnInitListener#onInit(int) onInit
	 */
	@Override
	public void onInit(int status) {
		Log.i(LOG_TAG, String.format("onInit(status: %d)", status));
		
		if (status == TextToSpeech.SUCCESS) {
			Log.v(LOG_TAG, "TTS engine inizializzato con successo");
			
			mTTS.setLanguage(Locale.ITALIAN);   // TODO: solo per emulatore
			mTTS.speak(getString(R.string.tts_init_ok), TextToSpeech.QUEUE_FLUSH, null);
			
			// Avvia la simulazione dello speech recognizer.
			this.simulaRiconoscimentoVocale();
		}
		else {
			Log.e(LOG_TAG, "Errore durante l'inizializzazione del TTS engine");
			
			String tickerText = getString(R.string.init_error);
			Toast.makeText(this, tickerText, Toast.LENGTH_LONG).show();
			
			Notification notification = new Notification(
					R.drawable.ic_launcher,   // TODO: usare icona apposita (es.: ic_stat_notify_msg)
					tickerText, System.currentTimeMillis());
			Intent intent = new Intent(this, VoiceControlServiceActivity.class);
			PendingIntent notificationIntent = PendingIntent.getActivity(this, 0, intent, 0);
			notification.setLatestEventInfo(this, getText(R.string.init_error_tts_title),
					getText(R.string.init_error_tts_text), notificationIntent);
			
			// Invia notifica.
			NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
			nm.notify(R.string.init_error, notification);
			
			// Termina il servizio.
			stopSelf();
		}
	}
	
	/**
	 * Dealloca le risorse utilizzate dal servizio, tra cui quelle impegnate
	 * dal motore di sintesi vocale e dal riconoscitore vocale.
	 * 
	 * @see android.app.Service#onDestroy() onDestroy
	 */
	@Override
	public void onDestroy() {
		Log.i(LOG_TAG, "onDestroy()");
		
		if (timer != null) {
			timer.cancel();
			timer.purge();
			timer = null;
		}
		
		if (mTTS != null) {  // Ferma la riproduzione dell'enunciato
			mTTS.stop();     // corrente e dealloca tutte le risorse
			mTTS.shutdown(); // utilizzate.
			Log.v(LOG_TAG, "TTS engine arrestato e risorse deallocate");
		}
		
		super.onDestroy(); // invocato per ultimo
	}
	
	/**
	 * Avvia l'applicazione a cui è stato associato l'ID (nome univoco) specificato.
	 * Se a questo ID non è stata associata alcuna applicazione, il servizio riproduce
	 * apposito messaggio vocale per informare l'utente che l'applicazione specificata
	 * non esiste o non è stata configurata.
	 * <p>
	 * Non appena l'applicazione specificata avrà completato la procedura di avvio,
	 * potrà collegarsi a questo servizio e inviargli una callback per consentirgli
	 * di comunicare con essa.
	 *  
	 * @param appId il nome univoco associato al package dell'applicazione
	 * @see #registerCallbackImpl(IServiceCallback)
	 */
	private void launchApp(String appId) {
		Log.i(LOG_TAG, String.format("launchApp(appId: %s)", appId));
		
		// Ottiene il nome del package dell'applicazione.
		String appPackageName = prefs.getPackageName(appId, null);
		
		Log.i(LOG_TAG, String.format("launchApp() - package name: %s", appPackageName));
		
		if (appPackageName != null && !appPackageName.isEmpty()) {
			// Imposta lo stato.
			mCurrentAppStatus = ApplicationStatus.LAUNCHING_APP;
			mCurrentApp = appId;
			
			// Invia feedback vocale.
			mTTS.speak(getString(R.string.tts_launching_app), TextToSpeech.QUEUE_ADD, null);
			
			// Avvia l'applicazione specificata.
			Intent launchIntent = getPackageManager().getLaunchIntentForPackage(appPackageName);
			startActivity(launchIntent);
		}
		else {
			Log.e(LOG_TAG, String.format("launchApp() package non valido: %s", appPackageName));
			
			mTTS.speak(getString(R.string.tts_launching_error), TextToSpeech.QUEUE_ADD, null);
		}
	}
	
	/**
	 * Permette di registrare l'interfaccia di callback per la comunicazione con l'applicazione
	 * controllata al fine di poterle inviare comandi specifici o il comando per richiederne la
	 * chiusura.
	 * <p>
	 * Questo metodo è invocato dall'applicazione controllata dopo che ha completato la procedura
	 * di avvio e consente ad essa di inviare un riferimento all'interfaccia di callback.
	 * 
	 * @param callback la callback per pilotare l'applicazione
	 * @see #launchApp(String)
	 */
	private void registerCallbackImpl(IServiceCallback callback) {
		Log.i(LOG_TAG, "registerCallbackImpl()");
		
		// Salva un riferimento alla callback per poter successivamente
		// inviare dei messaggi contenenti comandi per l'applicazione.
		mApplicationCallback = callback;
		
		// Imposta lo stato e invia un feedback all'utente.
		mCurrentAppStatus = ApplicationStatus.APP_RUNNING;
		mTTS.playSilence(250, TextToSpeech.QUEUE_FLUSH, null);
		mTTS.speak(getString(R.string.tts_app_started), TextToSpeech.QUEUE_ADD, null);
	}
	
	/**
	 * Invia il comando specificato all'applicazione correntemente attiva riproducendo
	 * l'apposito feedback vocale, anche in caso di errore durante l'invio del comando.
	 * <p>
	 * Quando l'applicazione riceverà il comando, lo analizzerà per poterlo eventualmente
	 * eseguire e dopo l'esecuzione l'esito sarà inviato a questo servizio, in cui verrà
	 * invocato il metodo {@link #resultFromExecuteImpl(boolean, String) resultFromExecuteImpl}.
	 * 
	 * @param params Bundle contenente il comando da inviare all'applicazione
	 * @see #resultFromExecuteImpl(boolean, String)
	 */
	private void executeCommand(Bundle params) {
		Log.i(LOG_TAG, String.format("executeCommand(params: %s)", params.toString()));
		
		if (mApplicationCallback != null) {
			try {
				mCurrentAppStatus = ApplicationStatus.EXECUTING_CMD;
				mTTS.speak(getString(R.string.tts_sending_cmd), TextToSpeech.QUEUE_ADD, null);
				mApplicationCallback.execute(params);   // invia il comando
			}
			catch (RemoteException e) {
				Log.e(LOG_TAG, "executeCommand(): RemoteException " + e.getMessage());
				
				mCurrentAppStatus = ApplicationStatus.APP_RUNNING;
				mTTS.playSilence(250, TextToSpeech.QUEUE_FLUSH, null);
				mTTS.speak(getString(R.string.tts_sending_cmd_error), TextToSpeech.QUEUE_ADD, null);
			}
		}
		else {
			// L'applicazione ha effettuato l'unbind dal servizio,
			// ciò ma dovrebbe essere possibile soltanto mentre si
			// trova nello stato ApplicationStatus.CLOSING.
			Log.e(LOG_TAG, "executeCommand() callback null");
		}
	}
	
	/**
	 * Permette di conoscere l'esito relativo all'esecuzione del comando precedentemente inviato,
	 * cioè se è stato eseguito correttamente o meno e un eventuale messaggio personalizzato da
	 * riprodurre vocalmente. Se il messaggio è <code>null</code> o vuoto, il servizio lo ignora
	 * e ne riproduce uno predefinito.
	 * <p>
	 * Questo metodo viene invocato dall'applicazione controllata, non appena termina l'esecuzione
	 * di un comando. Oltre all'esito, l'applicazione può anche includere un messaggio di testo
	 * personalizzato per farlo riprodurre vocalmente dal servizio: ad esempio, potrebbe essere
	 * utilizzato per spiegare l'errore che si è verificato e il modo in cui correggerlo, ecc..
	 * 
	 * @param success true se il comando è stato eseguito correttamente, false altrimenti
	 * @param utterance messaggio di testo personalizzato da riprodurre vocalmente
	 * @see #executeCommand(Bundle)
	 */
	private void resultFromExecuteImpl(boolean success, String utterance) {
		Log.i(LOG_TAG, String.format("resultFromExecuteImpl(success: %b, utterance: %s)", success, utterance));
		
		mCurrentAppStatus = ApplicationStatus.APP_RUNNING;
		mTTS.playSilence(250, TextToSpeech.QUEUE_FLUSH, null);
		if (utterance == null || utterance.isEmpty()) {
			mTTS.speak(
					getString(success ? R.string.tts_cmd_completed : R.string.tts_cmd_error),
					TextToSpeech.QUEUE_ADD,
					null);
		}
		else mTTS.speak(utterance, TextToSpeech.QUEUE_ADD, null);
	}
	
	/**
	 * Invia una richiesta di chiusura all'applicazione correntemente aperta che, poco prima
	 * di chiudersi, invierà un feedback invocando {@link #confirmClosingImpl() confirmClosingImpl}
	 * in modo che il servizio di controllo vocale lo riceva per riprodurre un apposito feedback
	 * vocale destinato all'utente.
	 * 
	 * @param params Bundle riservato per usi futuri
	 * @see #confirmClosingImpl()
	 */
	private void closeApp(Bundle params) {
		Log.i(LOG_TAG, String.format("closeApp(params: %s)", params));
		
		if (mApplicationCallback != null) {
			try {
				mCurrentAppStatus = ApplicationStatus.CLOSING_APP;
				mTTS.speak(getString(R.string.tts_closing_app), TextToSpeech.QUEUE_ADD, null);
				mApplicationCallback.close(params);   // invia la richiesta di chiusura
			}
			catch (RemoteException e) {
				Log.e(LOG_TAG, "closeApp(): RemoteException " + e.getMessage());
				
				mCurrentAppStatus = ApplicationStatus.APP_RUNNING;
				mTTS.playSilence(250, TextToSpeech.QUEUE_FLUSH, null);
				mTTS.speak(getString(R.string.tts_closing_error), TextToSpeech.QUEUE_ADD, null);
			}
		}
		else {
			// L'applicazione ha effettuato l'unbind dal servizio,
			// ciò ma dovrebbe essere possibile soltanto mentre si
			// trova nello stato ApplicationStatus.CLOSING.
			Log.e(LOG_TAG, "closeApp(): callback null");
		}
	}
	
	/**
	 * Riceve conferma della chiusura dell'applicazione richiesta tramite il metodo
	 * {@link #closeApp(Bundle) closeApp} e riproduce un apposito messaggio vocale
	 * per renderlo noto all'utente.
	 * 
	 * @see #closeApp(Bundle)
	 */
	private void confirmClosingImpl() {
		Log.i(LOG_TAG, "confirmClosingImpl()");
		
		mCurrentAppStatus = ApplicationStatus.NOTHING;
		mTTS.playSilence(250, TextToSpeech.QUEUE_FLUSH, null);
		mTTS.speak(getString(R.string.tts_app_closed), TextToSpeech.QUEUE_ADD, null);
	}
	
	/**
	 * Consente di informare l'applicazione controllata circa i cambiamenti di stato
	 * del riconoscitore vocale, cioè quando viene attivato o disattivato, oppure se
	 * si verificano errori durante la sua esecuzione.
	 * <p>
	 * Questo metodo dovrebbe essere invocato da determinati metodi dell'interfaccia
	 * di callback <code>RecognitionListener</code> associata allo speech recognizer,
	 * cioè da quelli che permettono di rilevarne lo stato:
	 * <ul>
	 * <li>{@link android.speech.RecognitionListener#onReadyForSpeech(Bundle) onReadyForSpeech}
	 * dovrebbe invocare questo metodo per segnalare che è attivo senza errori;</li>
	 * <li>{@link android.speech.RecognitionListener#onEndOfSpeech() onEndOfSpeech} dovrebbe
	 * invocare questo metodo per segnalare che è stato disattivato senza errori;</li>
	 * <li>{@link android.speech.RecognitionListener#onEndOfSpeech() onEndOfSpeech} dovrebbe
	 * invocare questo metodo per segnalare che è stato disattivato senza errori;</li>
	 * <li>{@link android.speech.RecognitionListener#onError(int)} dovrebbe invocare questo
	 * metodo per segnalare il codice d'errore che si è verificato.</li>
	 * </ul>
	 * 
	 * @param active <code>true</code> se lo speech recognizer è attivo,
	 *               <code>false</code> altrimenti
	 * @param error il codice dell'eventuale errore
	 * @see android.speech.RecognitionListener
	 * @see android.speech.SpeechRecognizer
	 */
	private void setListeningStatus(boolean active, int error) {
		Log.i(LOG_TAG, String.format("setListeningStatus(active: %b, error: %d)", active, error));
		
		if (mApplicationCallback != null && mCurrentAppStatus == ApplicationStatus.APP_RUNNING)
			try {
				Log.v(LOG_TAG, "setListeningStatus(): richiama listening");
				mApplicationCallback.listening(active, error);
			} catch (RemoteException e) {
				Log.e(LOG_TAG, "setListeningStatus(): RemoteException " + e.getMessage());
			}
	}
	
	/**
	 * Elabora i risultati provenienti dal motore di riconoscimento vocale ed intraprende
	 * l'azione corrispondente informando l'utente mediante un feedback vocale. I risultati
	 * ricevuti vengono elaborati soltanto se non è attiva alcuna applicazione oppure se da
	 * attiva è in attesa di comandi: nel primo caso potrebbe trattarsi della richiesta di
	 * avvio di un'applicazione, mentre nel secondo caso di un comando da inviare ad essa
	 * oppure una richiesta di chiusura. Se l'applicazione si trova in uno stato diverso,
	 * i risultati vengono ignorati.
	 * <p>
	 * Questo metodo viene invocato ogni volta che lo speech recognizer riceve risultati
	 * relativi ad una nuova operazione di riconoscimento vocale. La lista contenente gli
	 * eventuali risultati deve essere un oggetto non <code>null</code>.
	 * 
	 * @param results la lista non <code>null</code> contenente i risultati relativi
	 *                ad un'operazione di riconoscimento vocale
	 */
	private void processingResults(ArrayList<String> results) {
		Log.i(LOG_TAG, String.format("processingResults(results: %s)", results.toString()));
		
		/*
		 * Nessuna applicazione attiva.
		 * Ci si aspetta una richiesta vocale per avviare un'applicazione.
		 */
		if (mCurrentAppStatus == ApplicationStatus.NOTHING) {
			// Comando atteso per l'avvio di un'applicazione.
			String expected_cmd_prefix = getString(R.string.keywords_cats) // call attention to speech
					+ ' ' + getString(R.string.keywords_launch) + ' ';
			
			// Applicazioni disponibili da avviare.
			ArrayList<String> available_apps = new ArrayList<String>(
					prefs.getStringSet("AvailableApps", null));
			
			Log.v(LOG_TAG, String.format("Rilevazione comando: %s", results.toString()));
			Log.v(LOG_TAG, String.format("Comandi attesi: %s%s", expected_cmd_prefix, available_apps.toString()));
			
			// Indice restituito dal motore decisionale: se non è negativo,
			// permette di accedere al nome dell'applicazione da avviare.
			int decision_index = mDE.getExpectedString(expected_cmd_prefix, available_apps, results, 15);
			
			if (decision_index == DecisionEngine.NO_MATCH) {
				Log.v(LOG_TAG, "Comando non valido o applicazione non configurata");
				mTTS.speak(getString(R.string.tts_start_error), TextToSpeech.QUEUE_FLUSH, null);
			}
			else if (decision_index == DecisionEngine.MULTIPLE_MATCHES) {
				Log.v(LOG_TAG, "Indecisione tra almeno due comandi");
				mTTS.speak(getString(R.string.tts_repeat_cmd), TextToSpeech.QUEUE_FLUSH, null);
			}
			else {
				launchApp(available_apps.get(decision_index));
			}
			return;
		}
		
		/*
		 * Applicazione in esecuzione.
		 * Ci si aspetta uno dei comandi specifici dell'applicazione.
		 */
		if (mCurrentAppStatus == ApplicationStatus.APP_RUNNING) {
			// Comandi attesi per l'applicazione corrente.
			String expected_cmd_prefix = getString(R.string.keywords_cats) + ' ';
			ArrayList<String> expected_commands_app = new ArrayList<String>(prefs.getAppCommands(mCurrentApp));
			expected_commands_app.add(getString(R.string.keywords_finish));
			
			Log.v(LOG_TAG, String.format("Rilevazione comando: %s", results.toString()));
			Log.v(LOG_TAG, String.format("Comandi attesi: %s%s", expected_cmd_prefix, expected_commands_app.toString()));
			
			int decision_index = mDE.getExpectedString(expected_cmd_prefix, expected_commands_app, results, 15);
			
			if (decision_index == DecisionEngine.NO_MATCH) {
				Log.v(LOG_TAG, "Comando non valido");
				
				mTTS.playSilence(250, TextToSpeech.QUEUE_FLUSH, null);
				mTTS.speak(getString(R.string.tts_invalid_cmd), TextToSpeech.QUEUE_FLUSH, null);
			}
			else if (decision_index == DecisionEngine.MULTIPLE_MATCHES) {
				Log.v(LOG_TAG, "Indecisione tra almeno due comandi");
				
				mTTS.playSilence(250, TextToSpeech.QUEUE_FLUSH, null);
				mTTS.speak(getString(R.string.tts_repeat_cmd), TextToSpeech.QUEUE_FLUSH, null);
			}
			else if (decision_index < expected_commands_app.size()-1) {
				this.executeCommand(prefs.getAppCommand(mCurrentApp,
						expected_commands_app.get(decision_index)));
			}
			else {
				this.closeApp(null);
			}
			return;
		}
	}
	
	
	/**
	 * Questo metodo consente di simulare l'arrivo dei risultati dallo speech recognizer.
	 */
	private void simulaRiconoscimentoVocale() {
		// TODO: questo metodo serve soltanto per simulare l'arrivo dei risultati dallo speech recognizer.
		// Al suo posto si dovrebbe implementare il metodo onResults() dell'interfaccia RecognitionListener
		// e al suo interno invocare il metodo processingResults() sui risultati ricevuti.
		
		TimerTask task = new TimerTask() {
			int counter = 0;
			
			@Override
			public void run() {
				Log.v(LOG_TAG, "Servizio in esecuzione: " + counter);
				setListeningStatus(true, 0);
				
				switch(counter) {
				case 5:
					setListeningStatus(false, 0);
					processingResults(Voce.getSimulatedVoice01()); // android avvia demo
					break;
				case 10:
					setListeningStatus(false, 0);
					processingResults(Voce.getSimulatedVoice02()); // android esegui primo comando
					break;
				case 15:
					setListeningStatus(false, 0);
					processingResults(Voce.getSimulatedVoice03()); // android esegui secondo comando
					break;
				case 20:
					setListeningStatus(false, 0);
					processingResults(Voce.getSimulatedVoice04()); // android esegui terzo comando
					break;
				case 25:
					setListeningStatus(false, 0);
					processingResults(Voce.getSimulatedVoice05()); // android chiudi applicazione
					break;
				default:
					break;
				}
				
				counter++;
			}
		};
		timer = new Timer();
		timer.schedule(task, 0, 5000);
	}

}
