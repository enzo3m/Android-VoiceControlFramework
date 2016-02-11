package org.semm.android.vcf;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * Una semplice activity per avviare e terminare il servizio di controllo vocale.
 * 
 * @author vincenzo
 *
 */
public class VoiceControlServiceActivity extends Activity {
	
	/* Tag per il log. */
	private final static String LOG_TAG = VoiceControlServiceActivity.class.getSimpleName();
	
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Button startButton = new Button(this);
		startButton.setText("Avvia il servizio");
		startButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startVoiceService();
			}
		});
		Button stopButton = new Button(this);
		stopButton.setText("Arresta il servizio");
		stopButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				stopVoiceService();
			}
		});
		
		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.addView(startButton);
		layout.addView(stopButton);
		setContentView(layout);
		Log.i(LOG_TAG, "onCreate()");
    }
    
    /** Chiamato quando l'activity viene distrutta. */
    @Override
	protected void onDestroy() {
    	Log.i(LOG_TAG, "onDestroy()");
    	super.onDestroy();
	}
    
    /**
     * Avvia il servizio di controllo vocale.
     */
	private void startVoiceService() {
		startService(new Intent(this, VoiceControlService.class));
	}
	
	/**
	 * Arresta il servizio di controllo vocale.
	 */
	private void stopVoiceService() {
		stopService(new Intent(this, VoiceControlService.class));
	}
	
}