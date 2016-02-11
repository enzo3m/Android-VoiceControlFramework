package org.semm.android.vcf;

/**
 * L'interfaccia <code>IServiceCallback</code> permette al servizio di controllo vocale
 * di inviare dati ad un'applicazione.
 * 
 * @author vincenzo
 *
 */
interface IServiceCallback {
    /**
     * Informa l'applicazione sullo stato del riconoscitore vocale.
     * 
     * @param active <code>true</code> se lo speech recognizer è attivo,
     *               <code>false</code> altrimenti
     * @param error l'eventuale codice d'errore restituito dallo speech recognizer
     * @throws RemoteException se si interrompe il collegamento tra il servizio e l'applicazione
     */
    void listening(boolean active, int error);
    
    /**
     * Invia un comando all'applicazione.
     * 
     * @param params dati relativi al comando
     * @throws RemoteException se si interrompe il collegamento tra il servizio e l'applicazione
     */
    void execute(in Bundle params);
    
    /**
     * Richiede all'applicazione di chiudersi.
     * 
     * @param params dati relativi al comando di chiusura
     * @throws RemoteException se si interrompe il collegamento tra il servizio e l'applicazione
     */
    void close(in Bundle params);
}
