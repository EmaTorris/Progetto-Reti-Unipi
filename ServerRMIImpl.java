import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.concurrent.ConcurrentHashMap;

public class ServerRMIImpl extends RemoteServer implements ServerUtenteRMI{
	
	
	private static final long serialVersionUID = 1L;
	private ConcurrentHashMap<String,Utente> tabellahash;
	
	public ServerRMIImpl(ConcurrentHashMap<String,Utente> tabella) {
		super();
		this.tabellahash=tabella;
		
	}

	// metodo utilizzato per la registrazione dell'utente (RMI)
	public void RegistraUtente(String nome, Utente u) throws RemoteException{
		if(tabellahash.putIfAbsent(nome,u)==null) {
			System.out.println("Utente registrato con successo");
			return;
		}
		System.out.println("Non è stato possibile registrare l'utente");
		return;
		}
	}



