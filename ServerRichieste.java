import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ServerRichieste implements Runnable{

	private static ConcurrentHashMap<String, Utente> tabella; // tabella che viene utilizzata per salvare i vari utenti
	private static ConcurrentHashMap<String, Documento> documenti; // tabella che viene utilizzata per salvare i vari documenti
	private ArrayList<InetAddress> indirizzi; // serve per memorizzare gli indirizzi di multicast già usati
	private static int porta = 5555; //Porta per legare il ServerSocket ad un indirizzo specifico (usata nel selector)
	private static Object syncIndirizzi = new Object(); // oggetto utilizzato per prendere la lock sull'arraylist degli indirizzi di multicast
	private static Object syncUtenti = new Object(); // oggetto utilizzato per prendere la lock sulla concurrenthashmap degli utenti
	private static Object syncDocumenti = new Object(); // oggetto utilizzato per prendere la lock sulla concurrenthashmap dei documenti


	public ServerRichieste(ConcurrentHashMap<String, Utente> t, ConcurrentHashMap<String, Documento> d) {
		this.documenti = d;
		this.tabella = t;
		this.indirizzi = new ArrayList<>();
	}

	//metodo usato per ottenere l'utente a partire dal socketaddress (campo memorizzato nell'utente)
	private synchronized Utente getUtente(SocketChannel s){
			Utente aux = tabella.search(1, (k, v) -> {
				try {

					if ((v.getCanale() != null) && v.getCanale().equals(s.getRemoteAddress())) {

						return v;
					}
				} catch (IOException e) {
					return null;
				}

				return null;
			});

			return aux;
	}

	// funzione hash utilizzata per ottenere una porta diversa per ogni utente. Tale porta verrà assegnata
	// ad una serversocketchannel attraverso la bind (serve per lo scambio file client-server)
	private int FunzioneHash(String nome){
		//applico la maschera per ottenere solo valori positivi
		int aux = ((nome.hashCode() & 0x7fffffff) % ((int)Math.pow(2,16) - 1));
		//se è una porta nota aggiungo 1024 così sono sicuro di non usare porte nel range 0-1024 (porte note)
		if(aux < 1024 ) aux = aux + 1024;
		return aux;
	}

	// metodo utilizzato per ottenere l'utente(se esiste) a partire dal nome
	public static synchronized Utente getUtenteByName(String nome){
		Utente aux = null;
		try {
			aux = tabella.get(nome);
		}
		catch(NullPointerException e){
			aux = null;
		}
		return aux;
	}

	// metodo utilizzato per verificare che effettivamente un utente sia presente all'interno della tabella
	private synchronized boolean Ifisregistrated(String nomeutente) {
		boolean check = false;
		try{
			check = tabella.containsKey(nomeutente);
			return check;
		}
		catch (NullPointerException e){
			check = false;
		}
		return check;
	}

	//metodo utilizzato per verificare che l'utente sia effettivamente registrato verificando sia nome che password
	private synchronized boolean Ifregistrated(String nomeutente, String password) {
		try {
			String p = tabella.get(nomeutente).getPassword();
			if (tabella.containsKey(nomeutente) == true && p.equals(password) == true)
				return true;
			return false;
		}
		catch (NullPointerException e){
			return false;
		}
	}

	// metodo utilizzato per capire se siamo sotto Windows
	private boolean isWindows(String OS) {

		return (OS.indexOf("win") >= 0);

	}

	// metodo utilizzato per capire se siamo sotto un sistema Unix
	private boolean isUnix(String OS) {

		return (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0 );

	}


	//metodo utilizzato per capire se un utente è online oppure no
	private synchronized boolean IfisOnline(String nomeutente) {
		try {
			if (tabella.get(nomeutente).IsOnline())
				return true;
			return false;
		}
		catch (NullPointerException e){
			return false;
		}
	}

	// metodo usato per capire se l'utente è in modalità editing
	private synchronized boolean IfisEditing(String nomeutente) {
		try {
			if ((IfisOnline(nomeutente) == true) && (tabella.get(nomeutente).IsEditing() == true))
				return true;
			return false;
		}
		catch(NullPointerException e){
			return false;
		}
	}

	private boolean Online(String nomeutente) {
		if (Ifisregistrated(nomeutente)) {
			tabella.get(nomeutente).BecomeOnline();
			return true;
		}
		return false;
	}

	// metodo usato per capire se l'utente è in modalità logged
	private synchronized boolean Ifislogged(String nomeutente) {
		if (Ifisregistrated(nomeutente) && (IfisOnline(nomeutente) == true))
			return true;
		return false;
	}

	// metodo utilizzato per capire se effettivamente un documento esista oppure no
	private synchronized boolean ExistDoc(String nomedocumento){
		try {
			if (documenti.containsKey(nomedocumento)==true) return true;
			return false;
		}
		catch (NullPointerException e){
			return false;
		}
	}

	// metodo usato per controllare se un utente è in modalità editing (lato documento)
	private synchronized boolean IfEditingDoc(String nomeUtente, String nomeDocumento, int numerosezione){
	    if(ExistDoc(nomeDocumento)){
	        Documento docaux = documenti.get(nomeDocumento);
	        SezioneDocumento sezaux = docaux.OttieniSezione(numerosezione);
	        if(nomeUtente.equals(sezaux.ottieniSodu())) return true;
        }
        return false;
    }


    //il metodo run verrà chiamato dalla classe Server la quale avvierà un thread, questo metodo avrà il compito di
	//servire le varie richieste inviate dai vari client.
	public void run() {
		try {
		ServerSocketChannel serversocketchannel = ServerSocketChannel.open();
		serversocketchannel.socket().bind(new InetSocketAddress(InetAddress.getLocalHost(), porta));
		serversocketchannel.configureBlocking(false);

		Selector selector = Selector.open();
		serversocketchannel.register(selector, SelectionKey.OP_ACCEPT);


		while (true) {

			System.out.println("attendo per nuove connessioni");
			selector.select();
			Set<SelectionKey> readyKeys = selector.selectedKeys();
			Iterator<SelectionKey> iterator = readyKeys.iterator();
			while (iterator.hasNext()) {
				SelectionKey key = iterator.next();
				iterator.remove();
				// accetto la connessione e registro il serversocketchannel per le operazioni


				if ((key.readyOps() & SelectionKey.OP_ACCEPT) != 0) {
					// ho il canale con il quale questa chiave è stata creata
					ServerSocketChannel channel1 = (ServerSocketChannel) key.channel();
					SocketChannel client = channel1.accept();

					client.configureBlocking(false);
					ByteBuffer output = ByteBuffer.allocate(4096);

					SelectionKey key2 = client.register(selector, SelectionKey.OP_READ, output);
				}



				// se ricevo una selectionkey con OP_READ
				else if ((key.readyOps() & SelectionKey.OP_READ) != 0) {

					try {
						SocketChannel client = (SocketChannel) key.channel();
						ByteBuffer output = ByteBuffer.allocate(4096);


						// leggo le informazioni inviate dal client e le salvo all'interno di output
						// i vari dati delle varie operazioni sono della seguente forma "informazione1-informazione2-..-informazioneN"
						// tutte separate da un "-" che funge da delimitatore
						client.read(output);
						output.flip();
						// leggo la dimensione dell'operazione
						int value = output.getInt();
						// controllo se ho ricevuto tutta l'operazione
						if (value == (output.limit() - output.position())) {
							// prendo il contenuto dell'operazione
							String operazione = new String(output.array(), 4, output.limit() - 4);

							//separo ogni informazione fornita dal client
							String[] parti = operazione.split("-");
							String op = parti[0];


							switch(op) {

								// caso in cui l'operazione richiesta è quella di login
								case "login":


									String user = parti[1];
									String password = parti[2];
									ByteBuffer risplogin = ByteBuffer.allocate(512);

									// controllo che l'utente sia registrato / online / modalità editing
									if ((Ifregistrated(user, password) == true) && (IfisOnline(user) == false) && (IfisEditing(user) == false)) {


									    synchronized (syncUtenti) {
											tabella.get(user).BecomeOnline();
											tabella.get(user).AssegnaCanale(client);
										}

										//riempio il bytebuffer con le informazioni necessarie che verranno gestite una volta che la selectionkey sarà "writable"
										risplogin.put("y-login-".getBytes());
										risplogin.put(user.getBytes());
										risplogin.put("-".getBytes());

									} else {

										risplogin.put("n-login-".getBytes());

									}

									client.register(selector, SelectionKey.OP_WRITE, risplogin);
									risplogin.flip();
									break;


								case "create":
									// caso in cui la richiesta del client è una create

									boolean tuttookcreate = true;
									String nomeDocumento = parti[1];
									String numeroSezioni = parti[2];
									Utente proprietario = null;
									int num = -2;
									ByteBuffer rispcreate = ByteBuffer.allocate(512);


									proprietario = getUtente(client);


									// se chi ha fatto la richiesta non esiste
									if (proprietario == null) {
										rispcreate.put("nu-create-".getBytes());
										tuttookcreate = false;

									}
									if (proprietario != null) {
										// se chi ha fatto la richiesta esiste ma non è loggato
										if (!Ifislogged(proprietario.getName())) {
											System.out.println("utente non e' loggato");
											rispcreate.put("nl-create-".getBytes());
											tuttookcreate = false;
										} else if (ExistDoc(nomeDocumento) == true) {
											// se chi ha fatto la richiesta esiste ma il documento che cerca di creare esiste anch'esso
											System.out.println("documento gia' esistente");
											rispcreate.put("e-create-".getBytes());
											tuttookcreate = false;
										}
										String nomeproprietario = proprietario.getName();

										num = Integer.parseInt(numeroSezioni) + 1;

									}

									if(tuttookcreate) {

										// se sono qui posso creare il documento, tutti i controlli sono andati a buon fine

										InetAddress multiaddress = InetAddress.getByName("java.sun.com");

										boolean trovato = false;
										// genero un indirizzo di multicast che mi servirà per le chat e controllo che non sia uno che ho già generato in passato
										synchronized (syncIndirizzi) {
											while (!multiaddress.isMulticastAddress() && !trovato) {
												int firstoct = ThreadLocalRandom.current().nextInt(239, 239 + 1);
												int secondoct = ThreadLocalRandom.current().nextInt(0, 255 + 1);
												int thirdoct = ThreadLocalRandom.current().nextInt(0, 255 + 1);
												int fourthoct = ThreadLocalRandom.current().nextInt(0, 255 + 1);
												String auxadrr = firstoct + "." + secondoct + "." + thirdoct + "." + fourthoct;

												multiaddress = InetAddress.getByName(auxadrr);
												if (indirizzi.contains(multiaddress)) trovato = true;

											}

											indirizzi.add(multiaddress);
										}

										// controllo su che sistema sto lavorando in modo da creare la cartella nel modo corretto (cartella che verrà usata per
										// memorizzare tutti i documenti creati)
										String os = System.getProperty("os.name").toLowerCase();
										String tempfolder = System.getProperty("java.io.tmpdir");

										if (isWindows(os)) tempfolder = tempfolder + "\\Creazione\\";

										if (isUnix(os)) tempfolder = tempfolder + "/Creazione/";

										if (!Files.exists(Paths.get(tempfolder))) Files.createDirectory(Paths.get(tempfolder));



										Path path = Paths.get(tempfolder + nomeDocumento);
										System.out.println("path: " + path);


										if (!Files.exists(path)) Files.createDirectory(path);



										Documento newdoc = new Documento(tempfolder + nomeDocumento, num, proprietario.getName(), multiaddress);

										// aggiungo il documento ai documenti esistenti

										documenti.putIfAbsent(nomeDocumento, newdoc);


										// salvo il nome del proprietario del documento

										proprietario.aggiungiAProprietario(nomeDocumento);


										System.out.println("ho creato il documento");

										rispcreate.put("y-create-".getBytes());


										}

									client.register(selector, SelectionKey.OP_WRITE, rispcreate);
									rispcreate.flip();

									break;

								case "share":

									// caso in cui la richiesta del client è una share

									boolean tuttookshare = true;

									Utente Proprdoc = getUtente(client);
									ByteBuffer rispshare = ByteBuffer.allocate(512);
									Utente utenteinvitato = null;
									String nomeSender = null;
									String nomeProprietarioDoc = null;
									String nomeInvitato = null;
									Documento docfile = null;
									String nomeDocInvitato = null;
									boolean checkutenteinvitato = false;

									// controllo se chi ha fatto la richiesta esiste
									if (Proprdoc == null){
										rispshare.put("nu-share-".getBytes());
										tuttookshare = false;
									}
									if (Proprdoc != null) {


										nomeInvitato = parti[2];

										nomeDocInvitato = parti[1];

										// salvo delle informazioni che mi servono per dei controlli successivi
										synchronized (syncUtenti) {
											utenteinvitato = tabella.get(nomeInvitato);
											checkutenteinvitato = utenteinvitato.verificaCollaboratore(nomeDocInvitato);

										}


										nomeSender = Proprdoc.getName();


										// se l'utente non è loggato
										if (!Ifislogged(nomeSender)) {
											System.out.println("utente non e' loggato");
											rispshare.put("pr-share-".getBytes());
											tuttookshare = false;
										}
										// se il nome del destinatario non esiste
										else if ((Ifisregistrated(nomeInvitato)) == false) {
											System.out.println("il nome del destinatario non essite");
											rispshare.put("de-share-".getBytes());
											tuttookshare = false;
										}
										// se il documendo non esiste
										else if (ExistDoc(nomeDocInvitato) == false) {
											System.out.println("il documento non esiste");
											rispshare.put("do-share-".getBytes());
											tuttookshare = false;
										}
										// se l'utente destinatario è già stato invitato
										else if (checkutenteinvitato == true) {
											System.out.println("l'utente destinatario non esiste");
											rispshare.put("di-share-".getBytes());
											tuttookshare = false;
										}
										//se il documento esiste (qui la condizione non è un "else if" ma un if altrimenti se questa
										//condizione fosse vera la successiva non verrebbe mai testata saltando quindi un controllo)
										if (ExistDoc(nomeDocInvitato) == true){
											synchronized (syncDocumenti) {
												nomeProprietarioDoc = documenti.get(nomeDocInvitato).getCreatore();
												docfile = documenti.get(nomeDocInvitato);
											}
										}
										// se il nome del proprietario è diverso da chi ha effettuato l'invito (quindi non è il proprietario)
										else if (nomeProprietarioDoc.equals(nomeSender) == false) {
											System.out.println("il nome del proprietario è diverso da chi ha effettuato l'invito");
											rispshare.put("pi-share-".getBytes());
											tuttookshare = false;
										}
									}

									// se ho passato tutti i controlli
									if (tuttookshare) {

										// se l'utente destinatario è online lo aggiungo alla lista dei collaboratori e gli invio l'invito
										if (utenteinvitato.IsOnline() == true) {

											docfile.aggiungiCollaboratore(nomeInvitato);
											utenteinvitato.aggiungiCollaboratore(nomeDocInvitato);
											utenteinvitato.aggiungiInvitoOnline(nomeDocInvitato);
											rispshare.put("y-share-".getBytes());
										} else {
											// se l'utente destinatario non è online lo aggiungo alla lista dei collaboratori e aggiungo l'invito alla
											// sua lista degli inviti offline

											docfile.aggiungiCollaboratore(nomeInvitato);
											utenteinvitato.aggiungiInvitoOffline(nomeDocInvitato);
											utenteinvitato.aggiungiCollaboratore(nomeDocInvitato);
											rispshare.put("y-share-".getBytes());
										}
									}

									client.register(selector, SelectionKey.OP_WRITE, rispshare);
									rispshare.flip();
									break;

								case "show":
									// caso in cui la richiesta del client è una show

									Utente ProprDocShow = getUtente(client);
									ByteBuffer rispshow = ByteBuffer.allocate(512);
									String NomeUtenteShow = null;

									String nomeDocShow = parti[1];
									String numeroSezShow = parti[2];
									String rispostashow = null;
									boolean checkshowcollaboratore = false;
									boolean checkshowproprietario = false;
									Utente p = null;
									int numsezshow = 0;
									Documento auxd = null;
									int numerosezionishow = -2;
									boolean auxu = false;
									boolean tuttookshow = true;

									// se l'utente che ha fatto la richiesta non esiste
									if (ProprDocShow == null){
										System.out.println("l'utente che ha fatto la richiesta non esiste");
										rispshow.put("nu-show-".getBytes());
										tuttookshow = false;
									}
									else {

										NomeUtenteShow = ProprDocShow.getName();
										// in questo caso voglio vedere tutto il documento e non solo una sezione
										if(numeroSezShow.equals("_1")) numsezshow = -1;
										else numsezshow = Integer.parseInt(numeroSezShow);


										// salvo delle informazioni che mi servono per dei controlli più avanti
										synchronized (syncUtenti) {
											p = tabella.get(NomeUtenteShow);
											checkshowcollaboratore = p.verificaCollaboratore(nomeDocShow);
											checkshowproprietario = p.verificaProprietario(nomeDocShow);
										}


										synchronized (syncDocumenti) {
											auxd = documenti.get(nomeDocShow);

											//ottengo la lista dei collaboratori del documento, dal documento. Prendo l'utente
											//(se c'è) dalla lista dei collaboratori.
											if (auxd != null) {

												List<String> listacollshow = auxd.OttieniCollaboratori();
												auxu = listacollshow.contains(NomeUtenteShow);

												// controllo il numero di sezione inviato se è diverso da -1 o più grande
												// del numero massimo consentito mando un messaggio di errore
												numerosezionishow = auxd.getNumerosezioni();
											}
										}




										// se chi ha fatto la richiesta non esiste ( o non è online) non è ne collaboratore ne proprietario del documento
										if (p == null && checkshowcollaboratore == false && !Ifislogged(NomeUtenteShow) && checkshowproprietario == false) {
											System.out.println("chi ha fatto la richiesta non esiste ");
											rispshow.put("u-show-".getBytes());
											tuttookshow = false;
										}


										// se non c'è ne il documento, ne nella lista dei collaboratori del documento
										else if (auxd == null && auxu == false) {
											System.out.println("non esiste ne il documento ne la lista dei collaboratori del documento");
											rispshow.put("d-show-".getBytes());
											tuttookshow = false;
										}

										//se il numero della sezione non è corretto
										else if (numsezshow != -1 && numsezshow > numerosezionishow) {
											System.out.println("il numero della sezione non è corretto");
											rispshow.put("s-show-".getBytes());
											tuttookshow = false;
										}

										//se sono arrivato qui ho passato tutti i controlli

									}
									if (tuttookshow) {
										rispshow.put("y-show-".getBytes());
										rispshow.put(nomeDocShow.getBytes());
										rispshow.put("-".getBytes());
										rispshow.put(numeroSezShow.getBytes());
										rispshow.put("-".getBytes());
										rispshow.put(ProprDocShow.getName().getBytes());
										rispshow.put("-".getBytes());
									}


									client.register(selector, SelectionKey.OP_WRITE, rispshow);
									rispshow.flip();
									break;


								case "list":
									// caso in cui la richiesta del client è una list
									String NomeDoc = null;
									String NomeCreatore = null;
									String NomeCollaboratori = null;
									String StringaFinale = null;
									boolean tuttooklist = true;
									Utente uList = getUtente(client);
									ByteBuffer risplist = ByteBuffer.allocate(512);

									// se chi ha fatto la richiesta non esiste ...
									if (uList == null){
										risplist.put("nu-show-".getBytes());
										tuttooklist = false;
									}

									// ...se esiste
									if(tuttooklist == true) {
										List<String> collaboratori = null;
										List<String> listadocutenti = null;
										String[] auxnome = null;

										listadocutenti = uList.OttieniDocumenti();


										// se ci sono documenti
										if (listadocutenti.size() > 0) {
											for (String i : listadocutenti) {
												Documento doc = null;
												synchronized (syncDocumenti) {
													doc = documenti.get(i);
													auxnome = doc.getNomedocumento().split("/");
													collaboratori = doc.OttieniCollaboratori();
												}
												for (String j : collaboratori) {
													NomeCollaboratori = j + " ";
												}

												// mi serve solo il nome del file non tutto il percorso
												NomeDoc = "Documento: " + auxnome[auxnome.length - 1] + "\t";
												NomeCreatore = "Creatore: " + doc.getCreatore() + "\t";
												NomeCollaboratori = "Collaboratori: " + NomeCollaboratori + "\t";

												// stringa che verrà poi mandata al client con l'elenco di tutti i collaboratori e nome del proprietario
												// per ogni documento
												if (StringaFinale == null)
													StringaFinale = NomeDoc + NomeCreatore + NomeCollaboratori;
												else
													StringaFinale = StringaFinale + "\n" + NomeDoc + NomeCreatore + NomeCollaboratori;

												// se la dimensione del buffer non è sufficiente la raddoppio
												if(StringaFinale.getBytes().length > 512) risplist =  ByteBuffer.allocate((StringaFinale.getBytes().length)*2);

												risplist.put("y-list-".getBytes());
												risplist.put(StringaFinale.getBytes());
												risplist.put("-".getBytes());

											}
										} else {
											// se non ci sono documenti
											risplist.put("nd-list-".getBytes());
										}
									}
									client.register(selector, SelectionKey.OP_WRITE, risplist);
									risplist.flip();
									break;


								case "edit":

									// caso in cui la richiesta del client è una edit

                                    Utente editNomeRich = getUtente(client);

									boolean tuttookedit = true;
									ByteBuffer rispedit = ByteBuffer.allocate(512);
									String nomeEditDoc = parti[1];
									String numeroSezione = parti[2];

									// se l'utente che ha fatto la richiesta non esiste
									if (editNomeRich == null){
										rispedit.put("nu-edit-".getBytes());
										tuttookedit = false;
									}
									else {


										int numsez = Integer.parseInt(numeroSezione);

										Documento EditDoc = null;
										List<String> CollEditDoc = null;

										if (ExistDoc(nomeEditDoc)) {
											EditDoc = documenti.get(nomeEditDoc);
											CollEditDoc = EditDoc.OttieniCollaboratori();
										}

										String os = System.getProperty("os.name").toLowerCase();
										String tempfolder = System.getProperty("java.io.tmpdir");
										if (isWindows(os)) tempfolder = tempfolder + "\\Creazione\\";
										if (isUnix(os)) tempfolder = tempfolder + "/Creazione/";
										System.out.println("la cartella per i file temporanei e': " + tempfolder + nomeEditDoc);

										// se il propretario non esiste o non è online
										if ((Ifisregistrated(editNomeRich.getName()) == false) && (IfisOnline(editNomeRich.getName()) == false) && !Ifislogged(editNomeRich.getName())) {
											System.out.println(" il proprietario non esiste o non è online");
											rispedit.put("neu-edit-".getBytes());
											tuttookedit = false;
										}


										// se il documendo non esiste
										else if (ExistDoc(nomeEditDoc) == false) {
											System.out.println(" il documento non esiste o non è online");
											rispedit.put("ne-edit-".getBytes());
											tuttookedit = false;
										}

										// se il nome di chi ha fatto la richiesta non è ne proprietario ne collaboratore
										else if (EditDoc.getCreatore().equals(editNomeRich.getName()) == false && CollEditDoc.contains(editNomeRich.getName()) == false) {
											System.out.println("il nome di chi ha fatto la richiesta non è ne proprietario ne collaboratore");
											rispedit.put("np-edit-".getBytes());
											tuttookedit = false;
										}

										// se il numero di sezione è < 0 o > numero massimo di sezioni
										else if (EditDoc.getNumerosezioni() < numsez || numsez < -1) {
											System.out.println("il numero di sezione non è corretto");
											rispedit.put("s-edit-".getBytes());
											tuttookedit = false;
										}

										//se la sezione è già occupata
										else if (EditDoc.SezioneBloccata(numsez) == true) {
											System.out.println("la sezione è già occupata");
											rispedit.put("g-edit-".getBytes());
											tuttookedit = false;
										}

									}
                                    if(tuttookedit == true){
                                    	// se sono qui è andato tutto ok
										synchronized (syncUtenti) {
											tabella.get(editNomeRich.getName()).BecomeEditing();
										}
                                    	rispedit.put("y-edit-".getBytes());
                                    	rispedit.put(nomeEditDoc.getBytes());
                                    	rispedit.put("-".getBytes());
                                    	rispedit.put(editNomeRich.getName().getBytes());
                                    	rispedit.put("-".getBytes());
                                    	rispedit.put(numeroSezione.getBytes());
										rispedit.put("-".getBytes());
									}

									client.register(selector, SelectionKey.OP_WRITE, rispedit);
									rispedit.flip();

									break;



								case "send":
                                        //gestito lato client


									break;

								case "receive":
                                        //gestito lato client


									break;

								case "endedit":

									// caso in cui la richiesta del client è una endedit

                                    Utente endEditNomeRich = getUtente(client);
                                    ByteBuffer rispendedit = ByteBuffer.allocate(512);
									String nomeEndEdit = null;
									SocketChannel gestioneFile = null;

									// se l'utente che ha fatto la richiesta non esiste
                                    if(endEditNomeRich == null){
                                    	rispendedit.put("nu-endedit-".getBytes());
									}
                                    else {

										synchronized (syncUtenti) {
											nomeEndEdit = endEditNomeRich.getName();
											gestioneFile = endEditNomeRich.ottieniIndirizzoFile();
										}

										// se l'utente è in modalità editing
										if (Ifislogged(nomeEndEdit) && IfisEditing(nomeEndEdit)) {

											String nomeEndEditDoc = parti[1];
											String numeroSezioneEnd = parti[2];
											int numsezEnd = Integer.parseInt(numeroSezioneEnd);

											// verifico che sia in modalità editing lato documento (per conferma ma non è strettamente necessario)
											if (IfEditingDoc(nomeEndEdit, nomeEndEditDoc, numsezEnd)) {

												synchronized (syncDocumenti) {
													Documento endEditDoc = documenti.get(nomeEndEditDoc);

													// devo creare un nuovo Documento dove andrò ad inserire tutto quello che ricevo
													// dal client
													endEditDoc.AggiornaSezione(numsezEnd, gestioneFile);
													endEditDoc.OttieniSezione(numsezEnd).LiberaSez();
												}
												System.out.println("ho aggiornato la sezione");


												rispendedit.put("y-endedit-".getBytes());
												rispendedit.put(nomeEndEdit.getBytes());
												rispendedit.put("-".getBytes());

											} else {
												// non è in modalità editing
												System.out.println("non ok EndEdit");
												rispendedit.put("n-endedit-".getBytes());
											}
										} else {
											// non è in modalità editing
											rispendedit.put("n-endedit-".getBytes());
										}
									}
									client.register(selector, SelectionKey.OP_WRITE, rispendedit);
                                    rispendedit.flip();
									break;

								case "logout":

									// caso in cui la richiesta del client è una logout

									boolean tuttooklogout = true;
									Utente aux = getUtente(client);
									ByteBuffer risplogout = ByteBuffer.allocate(512);
									String usr = null;

									// se l'utente che ha fatto la richiesta non esiste
									if(aux == null){
										risplogout.put("n-logout-".getBytes());
										tuttooklogout = false;
									}

									if(tuttooklogout) {
										usr = aux.getName();

										// se l'utente è registrato e online lo porto offline
										if ((Ifisregistrated(usr) == true) && (IfisOnline(usr) == true)) {
											tabella.get(usr).BecomeOffline();
											System.out.println("ok");
											risplogout.put("y-logout-".getBytes());
											aux.chiudiCanale();
											aux.ottieniServerGestioneFile().socket().close();
										} else {
											System.out.println("non ok");
											risplogout.put("n-logout-".getBytes());
										}
									}

									client.register(selector, SelectionKey.OP_WRITE, risplogout);
									risplogout.flip();
									break;

							}

						}
					} catch (IOException | RuntimeException e) {
						System.out.println("ERRORE: " + e.getMessage());
						key.cancel();
					}
				}




				// se ricevo una selectionkey con OP_WRITE
				else if ((key.readyOps() & SelectionKey.OP_WRITE) != 0) {
					try {

						SocketChannel client = (SocketChannel) key.channel();

						// All'interno di questo buffer ho l'esito dell'operazione eseguita con eventuali informazioni aggiuntive
						// anche in questo caso tutte le informazioni sono separate da una "-" usato come delimitatore

						ByteBuffer buffer = (ByteBuffer) key.attachment();
						String rispOp = new String(buffer.array());
						String[] partirisp = rispOp.split("-");
						String risp = partirisp[0];
						String op = partirisp[1];
						ByteBuffer RispClient = ByteBuffer.allocate(512);

						switch (op){

							case "login":

								// caso in cui ho eseguito una login ora voglio mandare la risposta al client

								// se i controlli erano andati a buon fine
								if(risp.equals("y")){


									String user = partirisp[2];
									Utente u = getUtenteByName(user);
									List<String> messaggi = null;
									user.replaceAll(" ","");

									// controllo se quando ero offline ho ricevuto qualche invito tramite "share"
									synchronized (syncUtenti) {
										if (tabella.get(user).ListaInvitiOffline().size() != 0) {
											messaggi = tabella.get(user).ListaInvitiOffline();
											System.out.println("c'e' almeno un messaggio");
										}
									}


									int lunghmesslogin = "y-login effettuato con successo-".getBytes().length;
									RispClient.putInt(lunghmesslogin);
									RispClient.put("y-login effettuato con successo-".getBytes());

									// se ho ricevuto qualche messaggio mentre ero offline
									if(messaggi != null) {

										Iterator<String> mess = messaggi.iterator();
										while(mess.hasNext()) {

											String me = mess.next();
											System.out.println(me);
											byte[] mes = me.getBytes();
											RispClient.put(mes);
											RispClient.put(";".getBytes());

											lunghmesslogin = lunghmesslogin + mes.length + 1;

										}
										int posizionebuffer = RispClient.position();
										RispClient.position(0);
										RispClient.putInt(lunghmesslogin);
										RispClient.position(posizionebuffer);
									}

									// mando la risposta al client
									int numero = 0;

                                    RispClient.flip();
									while(RispClient.hasRemaining()) {
										 numero = client.write(RispClient);
										 //System.out.println("position: "+ RispClient.position());
										 //System.out.println("limit: " + RispClient.limit());
									}


									SocketChannel gestioneFile = null;
									ServerSocketChannel servergestioneFile = null;

									// questa porta mi serve nel caso in cui volessi usare operazioni che richiedono un trasferimento
									// di file dal client al server o viceversa.
									int portaFile = FunzioneHash(u.getName());

									// se non ho registrato una socketchannel per l'utente corrente la registro
									if(u.ottieniIndirizzoFile() == null || !u.ottieniIndirizzoFile().isConnected()){


										servergestioneFile = ServerSocketChannel.open();
										servergestioneFile.socket().bind(new InetSocketAddress(portaFile));
										u.salvaServerGestioneFile(servergestioneFile);
										gestioneFile = u.ottieniServerGestioneFile().accept();


										u.salvaIndirizzoFile(gestioneFile);

									}

									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("n")){

									// se l'operazione di login ha riportato qualche errore lo notifico al client
									int lunghmesslogin = "n-login non effettuata con successo-".getBytes().length;
									RispClient.putInt(lunghmesslogin);
									RispClient.put("n-login non effettuato con successo-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}

								break;


							case "create":

								if(risp.equals("y")){

									// se l'operazione è andata a buon fine lo notifico al client

									int lunghmesscreate = "y-create effettuato con successo-".getBytes().length;
									RispClient.putInt(lunghmesscreate);
									RispClient.put("y-create effettuato con successo-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}

									client.register(selector, SelectionKey.OP_READ);

								}
								else if (risp.equals("e")){

									// se il documento esiste già lo notifico al client
									int lunghmesscreate = "e-documento gia' esistente-".getBytes().length;
									RispClient.putInt(lunghmesscreate);
									RispClient.put("e-documento gia' esistente-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);
								}

								else if (risp.equals("nl")){

									// se l'utente non è loggato lo notifico al client
									int lunghmesscreate = "nl-l'utente non e' loggato-".getBytes().length;
									RispClient.putInt(lunghmesscreate);
									RispClient.put("nl-l'utente non e' loggato-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);
								}

								else if (risp.equals("nu")){

									// se l'utente non esiste lo notifico al client
									int lunghmesscreate = "nu-l'utente non esiste-".getBytes().length;
									RispClient.putInt(lunghmesscreate);
									RispClient.put("nu-l'utente non esiste-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);
								}


								break;

							case "share":

								if(risp.equals("pr")){

									// se il proprietario non esiste lo notifico al client
									int lunghmessshare = "pr-proprietario non esiste-".getBytes().length;
									RispClient.putInt(lunghmessshare);
									RispClient.put("pr-proprietario non esiste-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("nu")){

									// se l'utente richiedente non esiste lo notifico al client
									int lunghmessshare = "nu-utente richiedente non esiste-".getBytes().length;
									RispClient.putInt(lunghmessshare);
									RispClient.put("nu-utente richiedente non esiste-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("de")){

									// se il destinatario non esiste lo notifico al client
									int lunghmessshare = "de-destinatario non esiste-".getBytes().length;
									RispClient.putInt(lunghmessshare);
									RispClient.put("de-destinatario non esiste-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("do")){

									// se il documento non esiste lo notifico al client
									int lunghmessshare = "do-documento non esiste-".getBytes().length;
									RispClient.putInt(lunghmessshare);
									RispClient.put("do-documento non esiste-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("pi")){

									// se il prorprietario è diverso da chi ha fatto l'invito lo notifico al client
									int lunghmessshare = "pi-proprietario diverso da chi ha fatto l'invito-".getBytes().length;
									RispClient.putInt(lunghmessshare);
									RispClient.put("pi-proprietario diverso da chi ha fatto l'invito-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("di")){

									// se il destinatario è già stato invitato lo notifico al client
									int lunghmessshare = "di-il destinarario e' gia' stato invitato-".getBytes().length;
									RispClient.putInt(lunghmessshare);
									RispClient.put("di-il destinatario e' gia' stato invitato-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("y")){

									// se ho passato tutti i controlli lo notifico al client
									int lunghmesslogin = "y-share effettuata con successo-".getBytes().length;

									RispClient.putInt(lunghmesslogin);
									RispClient.put("y-share effettuata con successo-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}

								break;

							case "invite":

								if(risp.equals("u")){


									RispClient.put("u-utente non esiste o non e' collaboratore di quel File".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("d")){

									RispClient.put("d-il documento non esiste o l'utente non e' presente nella lista dei collaboratori".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("s")){

									RispClient.put("s-il numero della sezione non e' corretto".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("y")){

									String lista = partirisp[2];

									RispClient.put("y-operazione effettuata con successo-".getBytes());
									RispClient.put(lista.getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);
								}

								break;

							case "edit":


								if(risp.equals("neu")){

									// se il proprietario non esiste o non è online lo notifico al client
									int lunghmessedit = "neu-proprietario non esiste o non e' online-".getBytes().length;
									RispClient.putInt(lunghmessedit);
									RispClient.put("neu-proprietario non esiste o non e' online-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("nu")){

									// se l'utente richidente non esiste lo notifico al client
									int lunghmessedit = "nu-utente richiedente non esiste-".getBytes().length;
									RispClient.putInt(lunghmessedit);
									RispClient.put("nu-utente richiedente non esiste-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("ne")){

									// se il documento non esiste lo notifico al client
									int lunghmessedit = "ne-documento non esiste-".getBytes().length;
									RispClient.putInt(lunghmessedit);
									RispClient.put("ne-documento non esiste-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("np")){

									// se chi ha fatto la richiesta non è nè proprietario nè collaboratore lo notifico al client
									int lunghmessedit = "np-chi ha fatto la richiesta non e' ne proprietario ne collaboratore-".getBytes().length;
									RispClient.putInt(lunghmessedit);
									RispClient.put("np-chi ha fatto la richiesta non e' ne proprietario ne collaboratore-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("g")){

									// se la sezione è già occupata lo notifico al client
									int lunghmessedit = "g-sezione gia' occupata-".getBytes().length;
									RispClient.putInt(lunghmessedit);
									RispClient.put("g-sezione gia' occupata-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("s")){

									// se il numero di sezione è fuori dal range lo notifico al client
									int lunghmessedit = "s-numero sezione non corretto-".getBytes().length;
									RispClient.putInt(lunghmessedit);
									RispClient.put("s-numero sezione non corretto-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("y")) {

									// caso in cui ho passato tutti i controlli
									String nomeEditDoc = partirisp[2];
									String editNomeRich = partirisp[3];
									String numerosez = partirisp[4];
									Utente u = getUtenteByName(editNomeRich);
									int numsez = Integer.parseInt(numerosez);
									String indMulticast = null;
									Documento EditDoc = null;

									// prelevo l'indirizzo di multicast utilizzato per la chat (in particolare operazioni send e receive lato client)
									synchronized (syncDocumenti) {
										EditDoc = documenti.get(nomeEditDoc);
										indMulticast = EditDoc.getIndirizzo().toString();
									}

									// notifico che è andato tutto ok e mando l'indirizzo di multicast al client

									int lunghmessedit = "y-edit effettuato con successo-".getBytes().length;
									lunghmessedit = lunghmessedit + indMulticast.getBytes().length;

									RispClient.putInt(lunghmessedit);
									RispClient.put("y-edit effettuata con successo-".getBytes());
									RispClient.put(indMulticast.getBytes());
									RispClient.flip();
									while (RispClient.hasRemaining()) {
										client.write(RispClient);
									}


									// verifico il sistema operativo su cui sto lavorando
									String os = System.getProperty("os.name").toLowerCase();
									String tempfolder = System.getProperty("java.io.tmpdir");
									if (isWindows(os)) tempfolder = tempfolder + "\\Creazione\\";
									if (isUnix(os)) tempfolder = tempfolder + "/Creazione/";
									System.out.println("la cartella per i file temporanei e': " + tempfolder + nomeEditDoc);

									//a questo punto la sezione non è occupata da nessuno; ( ho già passato i controlli)
									// occupo la sezione per chi ha effettuato la richiesta
									FileChannel file = null;
									synchronized (syncDocumenti){
										EditDoc.OccSez(editNomeRich, numsez);


										//apro la sezione occupata dal nuovo editor
										file = FileChannel.open(Paths.get(EditDoc.OttieniSezione(numsez).getNomeSezione()), StandardOpenOption.READ);
									}

									// utilizzo la socketchannel salvata nell'utente per inviare il file letto nella cartella /tmp/Creazione/ al client
									SocketChannel gestioneFile = u.ottieniIndirizzoFile();

									// uso il metodo transferTo per trasferire il file in quanto metodo più rapido rispetto al trasferimento mediante buffer
									boolean tutti = false;
									while (tutti == false) {
										long transfered = file.transferTo(file.position(), file.size() - file.position(), gestioneFile);
										//se ho trasferito meno bytes di quanto sia effettivamente la dimensione del file devo continuare a
										//spedire i rimanenti bytes.
										file.position(file.position() + transfered);
										if (file.position() == file.size()) {
											tutti = true;
											//System.out.println("sono stati spediti: " + file.position() + " byte su: " + file.size() + " del file");
											u.chiudiCanale();
											file.close();

										}
									}


									gestioneFile = u.ottieniServerGestioneFile().accept();
									u.salvaIndirizzoFile(gestioneFile);


									client.register(selector, SelectionKey.OP_READ);
								}
								break;

							case "endedit":

								if(risp.equals("y")) {

									// se l'operazione ha passato tutti i controlli
									synchronized (syncUtenti) {
										String nomeendedit = partirisp[2];
										Utente u = getUtenteByName(nomeendedit);
										SocketChannel gestioneFile = null;
										int lunghmessendedit = "y-endedit effettuato con successo-".getBytes().length;

										RispClient.putInt(lunghmessendedit);
										RispClient.put("y-endedit effettuata con successo-".getBytes());
										RispClient.flip();
										while (RispClient.hasRemaining()) {
											client.write(RispClient);
										}
										gestioneFile = u.ottieniServerGestioneFile().accept();
										// passo l'utente in modalità logged e salvo il nuovo socketchannel utilizzato per lo scambio
										// file
										u.BecomeNotEditing();
										u.salvaIndirizzoFile(gestioneFile);
									}
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("n")){

									// se l'utente non è in modalità editing per quel documento/sezione lo notifico al client
									int lunghmessendedit = "n-l'utente non e' in modalita' editing per quel documento/sezione-".getBytes().length;
									RispClient.putInt(lunghmessendedit);
									RispClient.put("n-l'utente non e' in modalita' editing per quel documento/sezione-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);
								}

								else if(risp.equals("nu")){

									// se l'utente non esiste lo notifico al client
									int lunghmessendedit = "nu-utente non esiste-".getBytes().length;
									RispClient.putInt(lunghmessendedit);
									RispClient.put("nu-utente non esiste-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}

								break;

							case "list":

								if(risp.equals("y")){

									// se ho passato tutti i controlli
									String StringaFinale = partirisp[2];

									int lunghmesslist = "y-list effettuata con successo-".getBytes().length;
									int lunghstringafinale = StringaFinale.getBytes().length;
									RispClient.putInt(lunghmesslist+lunghstringafinale+1);

									// se il bytebuffer non è abbastanza grande per contenere la stringa con le informazioni
									// per i documenti, raddoppio la dimensione
									if(lunghstringafinale > 512) RispClient = ByteBuffer.allocate(lunghstringafinale * 2 );
									RispClient.put("y-list effettuata con successo-".getBytes());
									RispClient.put(StringaFinale.getBytes());
									RispClient.put("-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									RispClient = ByteBuffer.allocate(512);
									client.register(selector, SelectionKey.OP_READ);

								}
								else if(risp.equals("nd")){

									// se non ci sono documenti per l'utente lo notifico al client
									int lunghmesslist = "nd-non ci sono documenti per l'utente-".getBytes().length;
									RispClient.putInt(lunghmesslist);
									RispClient.put("nd-non ci sono documenti per l'utente-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);
								}
								else if(risp.equals("nu")){

									// se l'utente non esiste lo notifico al client
									int lunghmesslist = "nu-utente non esiste-".getBytes().length;
									RispClient.putInt(lunghmesslist);
									RispClient.put("nu-utente non esiste-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}

								break;

							case "show":

								if(risp.equals("y")){

									// se i vari controlli sono andati a buon fine
									String nomeDocShow = partirisp[2];
									String numsez = partirisp[3];
									String nomeProprDoc = partirisp[4];
									SocketChannel gestioneFile = null;
									Utente u = null;
									Documento auxd = null;


									u = getUtenteByName(nomeProprDoc);
									gestioneFile = u.ottieniIndirizzoFile();

									int numsezshow = 0;
									// carattere speciale per capire se voglio visualizzare l'intero documento oppure no
									// ho dato un numero negativo al numero di sezione in caso voglia vedere l'intero documento
									if(numsez.equals("_1")) numsezshow = -1;
									else numsezshow = Integer.parseInt(numsez);

									int lunghezzashow = "y-show effettuata con successo-".getBytes().length;
									RispClient.putInt(lunghezzashow);
									RispClient.put("y-show effettuata con successo-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}

									String os = System.getProperty("os.name").toLowerCase();
									String tempfolder = System.getProperty("java.io.tmpdir");
									if (isWindows(os)) tempfolder = tempfolder + "\\Creazione\\";
									if (isUnix(os)) tempfolder = tempfolder + "/Creazione/";
									String rispostashow = null;
									System.out.println("la cartella per i file temporanei e': " + tempfolder + nomeDocShow);

									//caso in cui voglio vedere tutto il documento
									if (numsezshow == -1) {

										//stringa che verrà usata per rispondere al client
										rispostashow = "la/e sezione/i ";
										int contasezioni = 0;



											//controllo le sezioni bloccate
										synchronized (syncDocumenti) {
											auxd = documenti.get(nomeDocShow);
											for (int i = 1; i < auxd.getNumerosezioni(); i++) {
												if (auxd.SezioneBloccata(i)) {
													rispostashow = rispostashow + i + " " + "e'/sono in editing";
													contasezioni++;
												}
											}

											//se non ci sono sezioni sotto modifica
											if (contasezioni == 0)
												rispostashow = "Nessuna sezione e' sotto modifica";

											//vado a prendere le sezioni non ancora modificate, memorizzate in una cartella e
											// le mando al client
											for (int j = 1; j < auxd.getNumerosezioni(); j++) {
												// qui devo inserire il nome delle varie sezioni + j

												FileChannel fileshow = FileChannel.open(Paths.get(auxd.OttieniSezione(j).getNomeSezione()), StandardOpenOption.READ);
												// uso il metodo transferTo per trasferire il file in quanto metodo più rapido rispetto al trasferimento mediante buffer
												boolean tutti = false;
												long transfered = 0;
												while (tutti == false) {
													transfered = fileshow.transferTo(fileshow.position(), fileshow.size() - fileshow.position(), gestioneFile);
													//se ho trasferito meno bytes di quanto sia effettivamente la dimensione del file devo continuare a
													//spedire i rimanenti bytes.
													fileshow.position(fileshow.position() + transfered);
													if (fileshow.position() == fileshow.size()) {
														tutti = true;
														//System.out.println("sono stati spediti: " + fileshow.position() + " byte su: " + fileshow.size() + " della sezione " + j);

													}
												}

												fileshow.close();
											}
										}

										u.chiudiCanale();

									}

									//caso in cui voglio vedere solo una sezione
									else {


										FileChannel sezioneshow = null;

										synchronized (syncDocumenti) {
											auxd = documenti.get(nomeDocShow);

											//controllo se la sezione è sotto modifica, se lo è mando il messaggio di risposta al client
											if (auxd.SezioneBloccata(numsezshow)) {
												rispostashow = "la sezione " + numsezshow + " e' in editing";
												// devo mandare il messaggio di risposta al client
											} else {
												rispostashow = "la sezione " + numsezshow + " non e' in editing";
											}


											sezioneshow = FileChannel.open(Paths.get(auxd.OttieniSezione(numsezshow).getNomeSezione()), StandardOpenOption.READ);
										}
										// uso il metodo transferTo per trasferire il file in quanto metodo più rapido rispetto al trasferimento mediante buffer
										boolean tutti = false;
										long transfered = 0;
										while (tutti == false) {
											transfered = sezioneshow.transferTo(sezioneshow.position(), sezioneshow.size() - sezioneshow.position(), gestioneFile);
											//se ho trasferito meno bytes di quanto sia effettivamente la dimensione del file devo continuare a
											//spedire i rimanenti bytes.
											sezioneshow.position(sezioneshow.position() + transfered);
											if (sezioneshow.position() == sezioneshow.size()) {
												tutti = true;
												//System.out.println("sono stati spediti: " + sezioneshow.position() + " byte su: " + sezioneshow.size());
												u.chiudiCanale();
												sezioneshow.close();
											}
										}

									}


									gestioneFile = u.ottieniServerGestioneFile().accept();
									u.salvaIndirizzoFile(gestioneFile);


									// rispshow contiene il nome del documento
									String rispshow = partirisp[2];
									int lunghezzarispshow = "y-show effettuata con successo-".getBytes().length;
									// qui è necessario fare una clear per ripristinare i puntatori dopo la scrittura avvenuta in precedenza
									RispClient.clear();
									// invio al client oltre che alla dimensione, il nome del documento e la stringa contenente le sezioni sotto modifica
									RispClient.putInt(lunghezzarispshow + rispshow.getBytes().length + rispostashow.getBytes().length + 2);
									RispClient.put("y-show effettuata con successo-".getBytes());
									RispClient.put(rispshow.getBytes());
									RispClient.put("-".getBytes());
									RispClient.put(rispostashow.getBytes());
									RispClient.put("-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}

									client.register(selector, SelectionKey.OP_READ);
								}

								else if(risp.equals("nu")){

									// se l'utente non esiste lo notifico al client
									int lunghmessshow = "nu-utente non esiste-".getBytes().length;
									RispClient.putInt(lunghmessshow);
									RispClient.put("nu-utente non esiste-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);

								}

								else if(risp.equals("u")){

									// se l'utente non esiste o non è collaboratore di quel file lo notifico al client
									int lunghmessshow = "u-l'utente non esiste o non e' collaboratore di quel file-".getBytes().length;
									RispClient.putInt(lunghmessshow);
									RispClient.put("u-l'utente non esiste o non e' collaboratore di quel file-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);
								}

								else if(risp.equals("d")){

									// se il documento non esiste o l'utente non è presente nella lista dei collaboratori lo notifico al client
									int lunghmessshow = "d-il documento non esiste o l'utente non e' presente nella lista dei collaboratori-".getBytes().length;
									RispClient.putInt(lunghmessshow);
									RispClient.put("d-il documento non esiste o l'utente non e' presente nella lista dei collaboratori-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);
								}

								else if(risp.equals("s")){

									// se il numero di sezione non è corretto lo notifico al client
									int lunghmessshow = "s-il numero di sezione non e' corretto-".getBytes().length;
									RispClient.putInt(lunghmessshow);
									RispClient.put("s-il numero di sezione non e' corretto-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);
								}

								break;

							case"logout":

								if(risp.equals("y")){

									// se ho passato tutti i controlli notifico al client che l'operazione è andata a buon fine
									RispClient.putInt("y-disconnessione effettuata con successo-".getBytes().length);
									RispClient.put("y-disconnessione effettuata con successo-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);
								}
								else if(risp.equals("n")){

									// se non sono riuscito a fare la disconnessione lo notifico al client
									int lunghmesslogout = "n-disconnessione non effettuata con successo-".getBytes().length;
									RispClient.putInt(lunghmesslogout);
									RispClient.put("n-disconnessione non effettuata con successo-".getBytes());
									RispClient.flip();
									while(RispClient.hasRemaining()) {
										client.write(RispClient);
									}
									client.register(selector, SelectionKey.OP_READ);
								}

								break;
						}

					} catch (IOException | RuntimeException e) {
						System.out.println("ERRORE: " + e.getMessage());
						key.cancel();
					}
				}
			}
		}
	}
		catch(IOException e) {
			e.printStackTrace();
		}
	}
}
