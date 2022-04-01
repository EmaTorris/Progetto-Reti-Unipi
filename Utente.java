import java.io.IOException;
import java.io.Serializable;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Utente implements Serializable{
	
	private static final long serialVersionUID = 1L;
	private String Nome;
	private String password;
	private boolean online; // usato per capire se un utente è logged o no
	private boolean editing; // usato per capire se un utente è editing oppure no
	private List<String> Collaboratori; // usato per capire per quali documenti l'utente è collaboratore
	private List<String> DocProprietario; // usato per capire per quali documenti l'utente è proprietario
	private SocketAddress canale;
	private List<String> invitiOffline; //documenti a cui l'utente è stato invitato (offline)
	private List<String> invitiOnline; //documenti mentre l'utente è online
	private SocketChannel canaleFile;
	private ServerSocketChannel ch;

	public Utente(String nome, String password) {
		this.Nome = nome;
		this.password = password;
		this.DocProprietario = Collections.synchronizedList( new ArrayList<String>());
		this.Collaboratori = Collections.synchronizedList( new ArrayList<String>());
		this.invitiOffline = Collections.synchronizedList( new ArrayList<String>());
		this.invitiOnline = Collections.synchronizedList( new ArrayList<String>());
		this.online = false;
		this.editing = false;
		this.canaleFile = null;
		this.ch = null;
	}

	// metodo usato per ottenere il nome dell'utente
	public synchronized String getName(){
		return this.Nome;
	}

	// metodo usato per memorizzare la socketchannel ad ogni login
	public synchronized void AssegnaCanale(SocketChannel sock) throws IOException {
		this.canale = sock.getRemoteAddress();
	}

	// metodo usato per salvare la ServerSocketChannel utile per il trasferimento dei file client-server
	public synchronized void salvaServerGestioneFile(ServerSocketChannel sock){
		this.ch = sock;
	}

	// metodo che resituisce la ServerSocketChannel salvata in precedenza (che verrà poi usata per l'accept() )
	public synchronized ServerSocketChannel ottieniServerGestioneFile(){
		return this.ch;
	}

	// metodo che salva la SocketChannel dedicata al trasferimento dei file client-server
	public synchronized void salvaIndirizzoFile(SocketChannel sock) throws  IOException{
		this.canaleFile = sock;
	}

	// metodo che restituisce la SocketChannel precedentemente salvata
	public synchronized SocketChannel ottieniIndirizzoFile(){
		return this.canaleFile;
	}

	// metodo che si occupa di chiudere il canale dopo aver concluso il trasferimento del file ( mi serve per l' EOF )
	public synchronized void chiudiCanale(){
		try {
			this.canaleFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// metodo che restituisce la SocketAddress precedentemente salvata
	public synchronized SocketAddress getCanale() {
		return this.canale;
	}

	// metodo usato per verificare se l'utente è online oppure no
	public synchronized boolean IsOnline() {
		return this.online;
	}

	// metodo usato per verificare se l'utente è in modalità editing o no
	public synchronized boolean IsEditing() {return this.editing;}

	// metodo usato per notificare che l'utente ora è logged
	public synchronized void BecomeOnline() {
		this.online = true;
	}

	// metodo usato per notificare che ora l'utente non è logged (ha fatto il logout)
	public synchronized void BecomeOffline() {
		this.online = false;
	}

	// metodo usato per notificare che l'utente è in modalità editing
	public synchronized void BecomeEditing() {
		this.editing = true;
	}

	// metodo usato per notificare che l'utente non è più in modalità editing (end-edit)
	public synchronized void BecomeNotEditing() {
		this.editing = false;
	}

	// metodo che restituisce la password di un utente
	public String getPassword() {
		return this.password;
	}

	// metodo usato per restituire sia i documenti di cui un utente è proprietario sia i documenti di cui è collaboratore
	public synchronized List<String> OttieniDocumenti(){
		if(this.Collaboratori.isEmpty()) return this.DocProprietario;
		else{
			this.DocProprietario.addAll(this.Collaboratori);
			return  this.DocProprietario;
		}
	}

	// metodo usato per aggiungere un documento per cui è collaboratore alla lista
	public synchronized boolean aggiungiCollaboratore(String nome){
		if (Collaboratori.add(nome)) return true;
		return false;
	}

	// metodo usato per aggiungere un nuovo invito mentre l'utente è online
	public synchronized boolean aggiungiInvitoOnline(String nome){
		if (invitiOnline.add(nome)) return true;
		return false;
	}

	// metodo usato per aggiungere un documento per cui l'utente è proprietario
	public synchronized boolean aggiungiAProprietario(String nome){
		if (DocProprietario.add(nome)) return true;
		return false;
	}

	// metodo usato per verificare se un utente è proprietario di un documento
	public synchronized boolean verificaProprietario(String nome){
		if(DocProprietario.contains(nome)) return true;
		return false;
	}

	// metodo usato per verificare se un utente è collaboratore di un documento
	public synchronized boolean verificaCollaboratore(String nome){
		if(Collaboratori.contains(nome)) return true;
		return false;

	}

	// metodo usato per aggiungere un invito mentre l'utente è offline
	public synchronized boolean aggiungiInvitoOffline(String nome){
		if (invitiOffline.add(nome)) return true;
		return false;
	}

	// metodo usato per restituire la lista degli inviti mentre l'utente è offline
	public synchronized List<String> ListaInvitiOffline(){
		return this.invitiOffline;
	}

	// metodo usato per restituire la lista degli inviti mentre l'utente è online
	public synchronized List<String> ListaInvitiOnline(){
		return  this.invitiOnline;
	}

	// metodo usato per cancellare la lista degli inviti mentre un utente è online ( per evitare che eventuali inviti
	// vengano letti più volte
	public synchronized void CancellaInvitiOnline(){
		this.invitiOnline.clear();
	}
}
