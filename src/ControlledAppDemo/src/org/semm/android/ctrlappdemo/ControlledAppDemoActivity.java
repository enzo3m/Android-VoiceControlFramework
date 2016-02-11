package org.semm.android.ctrlappdemo;

import org.semm.android.vcf.app.ControlledActivity;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Esempio di applicazione VCF-capable.
 * 
 * @author vincenzo
 *
 */
public class ControlledAppDemoActivity extends ControlledActivity {
	
	private static final String LOG_TAG = ControlledAppDemoActivity.class.getSimpleName();
	
	private TextView m_SR_state;
	
	private Handler mHandler;
	
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(LOG_TAG, "onCreate()");
        
        m_SR_state = new TextView(this);
        m_SR_state.setText("Servizio di riconoscimento vocale: non attivo");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.TOP);
        layout.addView(m_SR_state);
        
        setContentView(layout);
        
        mHandler = new Handler();
    }
    
    @Override
    public void onListening(boolean active, int error) {
		Log.i(LOG_TAG, String.format("onListening(%b, %d)", active, error));
		final String state = (active ? "attivo" : "non attivo");
		runOnUiThread(new Runnable(){
			@Override
			public void run() {
				m_SR_state.setText("Servizio di riconoscimento vocale: " + state);
			}
		});
	}
    
    @Override
    protected void onExecute(Bundle params) {
		Log.i(LOG_TAG, "onExecute(): " + params);
		
		if (params.containsKey("command01")) {
			Log.i(LOG_TAG, "onExecute(): " + params.getString("command01"));
			mHandler.post(new Runnable(){
				@Override
				public void run() {
					Toast
					.makeText(ControlledAppDemoActivity.this, "Comando 1...", Toast.LENGTH_LONG)
					.show();
				}
			});
			try {
				Thread.sleep(3000);
				setExecuteResult(true, params.getString("command01") + " completato"); // msg personalizzato
			} catch (InterruptedException e) {
				Log.e(LOG_TAG, "Thread: " + e.getMessage());
			}
		}
		else if (params.containsKey("command02")) {
			Log.i(LOG_TAG, "onExecute(): " + params.getString("command02"));
			mHandler.post(new Runnable(){
				@Override
				public void run() {
					Toast
					.makeText(ControlledAppDemoActivity.this, "Comando 2...", Toast.LENGTH_LONG)
					.show();
				}
			});
			try {
				Thread.sleep(3000);
				setExecuteResult(true, null); // msg di default
			} catch (InterruptedException e) {
				Log.e(LOG_TAG, "Thread: " + e.getMessage());
			}
		}
		else {
			Log.w(LOG_TAG, "onExecute() - comando sconosciuto: " + params);
			setExecuteResult(false, "comando sconosciuto");
		}
	}

    @Override
    protected void onClose(Bundle params) {
		Log.i(LOG_TAG, "onClose(): " + params);
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "Thread: " + e.getMessage());
		}
		
		Log.i(LOG_TAG, "onClose(): chiama finish()");
		finish(); // l'activity principale è quella corrente
	}
    
}
