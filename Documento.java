import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.nio.channels.SocketChannel;
import java.util.*;

public class Documento {

	private int numerosezioni;
	private String nomedocumento;
	private String creatore;
	private List<String> nomipartecipanti;
	private SezioneDocumento[] sezioni;
	private InetAddress indirizzo;


	public Documento(String nomedocumento, int nsezioni, String proprietario, InetAddress indirizzo){
		this.numerosezioni = nsezioni - 1;
		this.nomedocumento = nomedocumento;
		this.indirizzo = indirizzo;
		this.creatore = proprietario;
		this.sezioni = new SezioneDocumento[nsezioni];
		for (int i = 1; i < nsezioni; i++){
			this.sezioni[i] = new SezioneDocumento(this.nomedocumento,i);
		}
		this.nomipartecipanti = Collections.synchronizedList(new ArrayList<String>());
	}

	// se c'è proprietario restituisco true altrimenti false
	public synchronized boolean SezioneBloccata(int sez){
		if (sezioni[sez].ottieniSodu() == null ) return false;
		else return true;

	}

	// metodo che mi restituisce se la sezione è occupata
	public synchronized void OccSez(String nome, int sez){
		sezioni[sez].OccupaSezPer(nome);
	}

	// metodo che aggiunge collaboratori al documento
	public synchronized void aggiungiCollaboratore(String nome){
		if(nome != null && !nomipartecipanti.contains(nome))
			nomipartecipanti.add(nome);
	}

	// metodo che restituisce una sezione di un documento
	public synchronized SezioneDocumento OttieniSezione(int numerosezione){
	    if(numerosezione <= numerosezioni && numerosezione > 0)
	        return this.sezioni[numerosezione];
	    return null;
    }

    // metodo che aggiorna il contenuto di una sezione
    public synchronized void AggiornaSezione(int numerosezione, SocketChannel client) throws FileNotFoundException {
	    SezioneDocumento aux = OttieniSezione(numerosezione);
	    if(aux != null)
            aux.AggiornaSezione(client);
	    else
	        System.out.println("operazione aggiornasezione non riuscita");
    }

    // metodo che restituisce i collaboratori di un documento
	public synchronized List<String> OttieniCollaboratori(){
	    return nomipartecipanti;
    }

	// metodo che restituisce il numero di sezioni di un documento
    public synchronized int getNumerosezioni(){
		return this.numerosezioni;
	}

	// metodo che restituisce il nome di un documento
	public synchronized String getNomedocumento(){
		return this.nomedocumento;
	}

	// metodo che restituisce l'indirizzo salvato per la chat di un documento
	public synchronized InetAddress getIndirizzo(){
		return this.indirizzo;
	}

	// metodo che restituisce il creatore del documento
	public synchronized String getCreatore(){
		return this.creatore;
	}
}

