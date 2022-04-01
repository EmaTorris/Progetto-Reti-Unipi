import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerUtenteRMI extends Remote {

		void RegistraUtente(String nome, Utente u) throws RemoteException;
}
