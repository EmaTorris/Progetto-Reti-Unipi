
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
	
	public static void main (String[] args) {
		ConcurrentHashMap<String,Utente> UtentiRegistrati = new ConcurrentHashMap<String,Utente>(); // concurrentHashMap utilizzata per salvare gli utenti
		ConcurrentHashMap<String,Documento> Documenti = new ConcurrentHashMap<>(); // concurrenthashmap utilizzata per salvare i documenti
		try {
			ServerRMIImpl ServerRMI = new ServerRMIImpl(UtentiRegistrati);
			ServerUtenteRMI stub = (ServerUtenteRMI) UnicastRemoteObject.exportObject(ServerRMI,0);
			LocateRegistry.createRegistry(7000);
			Registry r = LocateRegistry.getRegistry(7000);
			r.rebind("S", stub);
		} catch (RemoteException e) {
			e.printStackTrace();
		}

		// creo ed eseguo il thread che si occuperà delle varie operazioni fornite lato client
		ServerRichieste SRichieste = new ServerRichieste(UtentiRegistrati,Documenti);
		Thread eseguirichieste = new Thread(SRichieste);
		eseguirichieste.start();
		// eseguo un thread che sarà costantemente in ascolto di eventuali inviti (operazione show) mentre un utente è online
        InvitiOnlineServer lis = new InvitiOnlineServer();
        lis.start();
		
		
		
	}
}
