package org.semm.android.vcf;

import org.semm.android.vcf.IServiceCallback;

/**
 * L'interfaccia <code>IService</code> permette ad un'applicazione di inviare dati
 * al servizio di controllo vocale.
 * 
 * @author vincenzo
 *
 */
interface IService {
    /**
     * Registra l'interfaccia di callback sul servizio di controllo vocale
     * con lo scopo di poter ricevere comandi da esso.
     *  
     * @param callback l'interfaccia di callback per la comunicazione
     * @throws RemoteException se si interrompe il collegamento tra il servizio e l'applicazione
     */
    void registerCallback(IServiceCallback callback);
    
    /**
     * Notifica il servizio di controllo vocale in merito al completamento
     * dell'esecuzione del comando precedentemente inviato.
     * 
     * @param success <code>true</code> se il comando è stato eseguito correttamente,
     *                <code>false</code> altrimenti
     * @param message il messaggio che deve essere pronunziato dal servizio di controllo vocale
     * @throws RemoteException se si interrompe il collegamento tra il servizio e l'applicazione
     */
    void resultFromExecute(boolean success, String message);
    
    /**
     * Invia la conferma di chiusura al servizio di controllo vocale.
     * 
     * @throws RemoteException se si interrompe il collegamento tra il servizio e l'applicazione
     */
    void confirmClosing();
}
