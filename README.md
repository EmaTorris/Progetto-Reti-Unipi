# Progetto-Reti-Unipi
Progetto reti anno accademico 2018-2019
Turing: distributed collaborative editing


TURING (disTribUted collab-oRative edItiNG) è uno strumento per l’editing collaborativo di documenti basato su un classico servizio client-server. La creazione di tale strumento è stata fatta scegliendo un’interfaccia basata su linea di comando (CLI). L’applicazione si svolge nel seguente modo: l’utente, dopo aver effettuato la registrazione, esegue delle richieste che verranno poi gestite dal Server in attesa di risposta. Le operazioni messe a disposizione dell’utente verranno trattate nel dettaglio in una sezione apposita. In generale possiamo dire che oltre alla registrazione, l’utente può effettuare la creazione, la modifica e la condivisione di un documento ad altri utenti. Durante la fase di modifica è possibile chattare con tutti gli altri utenti collaboratori che stanno occupando le diverse sezioni del file. Le sezioni devono essere necessariamente diverse in quanto un utente può modificare una sezione solo se essa non è già sotto modifica. L’invito può essere fatto univocamente da chi ha creato il file e può essere effettuato indipendentemente dallo stato “online” o “offline” dell’utente destinatario.

**Implementazione delle varie operazioni con esempi d’uso:** 
Di seguito mostro le varie scelte implementative per le varie operazioni con alcuni esempi su come utilizzarle. Per eseguire le varie operazioni, dopo aver compilato con il compilatore javac i vari file, si esegue prima il server con il comando:

  _Java Server_
  
E solo in seguito si passa all’esecuzione del client:

_Java Client_

Solo successivamente è possibile eseguire le seguenti operazioni (lato client).

  **register:**
Per le varie richieste di registrazione ho utilizzato un sistema basato su RMI. Il client, comunicando con il server, utilizza i metodi di un oggetto remoto. Entrando nel dettaglio il server crea l’oggetto remoto e lo passa ad un Registry sotto forma di stub. Lo stub permette al client di usare i metodi definiti sul server in modalità “remota”.
Per eseguire la registrazione:

		Turing register [nome_utente] [password_utente]


  **login:**
Il sistema di login è implementato tramite una connessione TCP, tale operazione viene effettuata solo in seguito ad una registrazione altrimenti viene restituito un errore. La comunicazione avviene nel seguente modo: 
Client: accetta in ingresso un nome ed una password e dopo aver effettuato vari controlli invia al server tali informazioni sfruttando la socketchannel precedentemente connessa con il server. Dopo aver inviato queste informazioni si mette in attesa di leggere l’esito dell’operazione.
Server: il server, a questo punto, effettua dei controlli sull’ effettiva esistenza dell’utente. Se l’operazione è andata a buon fine cambia lo stato dell’utente da offline ad online salvando la socketchannel che ha permesso la connessione tra client e server. In seguito, cerco di connettere una nuova socketchannel che potrà essere utilizzata successivamente per operazioni che richiedono il trasferimento di file.
Client: una volta letto l’esito dell’operazione inviata dal server, a seconda di come essa si sia conclusa avvio un thread (invitionlineclient) che si occupa di verificare eventuali inviti per documenti mentre l’utente è online. Infine, effettuo la connect sulla socketchannel in attesa di essere accettata sul lato server.
Per eseguire il login:

		Turing login [nome_utente] [password]


  **logout:**
Per l’operazione di logout la gestione avviene tramite TCP. L’operazione infatti viene seguita nel seguente modo: 
Client: Invio l’operazione al server in attesa di sapere l’esito 
Server: Dopo aver ottenuto dal client l’operazione verifico se effettivamente l’utente è registrato e online. In tal caso invio l’esito positivo dell’operazione altrimenti mando un messaggio di errore al client.
Client: ricevo l’esito dell’operazione e ne stampo il messaggio in caso di errore 
Per eseguire il logout: 
		
		Turing logout 


  **create:**
Anche tale operazione è gestita tramite TCP. Infatti, si svolge nel seguente modo:
Client: dopo aver controllato che i vari dati di input siano corretti li mando al server in attesa di ricevere una risposta per l’effettiva operazione. 
Server: dopo aver ricevuto i dati di input effettuo dei controlli su di essi se i controlli sono andati tutti a buon fine genero un indirizzo di multicast che verrà sia salvato all’ interno del documento appena creato, sia nella lista di tutti gli indirizzi già memorizzati. Procedo nel controllo del sistema operativo sul quale lavoro, accedo alla cartella dei file temporanei e creo, se non esiste, la cartella contenente i documenti che sono stati creati. Dopo aver creato e registrato il documento con le rispettive sezioni all’ interno della cartella invio un messaggio al client con l’esito dell’operazione, dove in caso di errore mando il rispettivo messaggio.
Client: leggo la risposta dal server e stampo il messaggio in caso di errore.
 Per utilizzare l’operazione create:
	
	Turing create [miodocumento] [numerosezione]


  **share:**
la gestione per quest’operazione avviene tramite TCP. In questo caso l’operazione si svolge nel seguente modo (dobbiamo distinguere online da offline):
Client: dopo aver letto da input i vari dati effettuo un controllo su di essi prima di inviarli al server. Se i controlli sono andati a buon fine invio tali informazioni al server aspettando un esito. 
Server: controllo che tutte le informazioni ricevute dal client passino tutti i controlli. In tal caso controllo lo stato dell’utente a cui ho effettuato l’invito. Se l’utente è online aggiungo il suo nome alla lista dei collaboratori per tale documento. Inoltre, aggiungo il nome del documento alla lista dei documenti di cui è collaboratore (campo utente) e lo stesso nome (del documento) lo aggiungo alla lista degli inviti online. Nel caso in cui invece l’utente risulti offline lo aggiungo alla lista dei collaboratori del documento, aggiungo il nome del documento alla lista dei documenti per cui l’utente è collaboratore e inserisco il nome del documento alla lista degli inviti offline. Se l’operazione è andata a bon fine rispondo al client altrimenti mando l’eventuale messaggio di errore.
Client: il client a questo punto riceve l’esito della risposta inviata dal server e in caso di errore stampa il messaggio. 
Per eseguire l‘ operazione di share:

	Turing share [miodocumento] [nomeinvitato]


  **show:**
L’operazione di show viene gestita anch’ essa tramite TCP. Il client verifica i dati letti da input e a seconda del numero di sezione capisce se si vuole visualizzare l’intero documento o solo una sezione. Viene utilizzato per visualizzare l’intero documento un carattere speciale (_1).
Client: Invia le varie informazioni dopo averle accuratamente testate al server che si mette in attesa di una risposta.
Server: riceve i dati forniti da input ed effettua i controlli per vedere se l’operazione è possibile effettuarla senza errori. Attraverso la lettura del numero di sezione capisce se si vuole leggere l’intero documento o solo una sezione. Verifico che effettivamente esista la cartella dei file utilizzati per l’operazione create. In tal caso verifico se vi sono sezioni sotto modifica e le salvo in una stringa. La gestione che prevede l’invio della sezione (o del documento ) è basata su trasferimento TCP. Tuttavia, i file vengono trasferiti utilizzando un metodo basato su filechannel, ovvero la transferto che permette un invio molto più rapido dei dati. A seguito dell’esito dell’operazione invio la risposta al client con eventuali messaggi di errore.
Client: ricevo una risposta da parte del server e in caso esito positivo controllo, se esiste, la cartella nella quale salvare il documento ( o la sezione) richiesta. Se non esiste lo creo ed eseguo l’ operazione che mi permette di trasferire il file dal server al client. In caso di eventuali errori stampo il messaggio di errore.
Per eseguire l’operazione di show: 
	
	Turing show [nomedocumento] [eventualisezioni]


  **list:**
Per l’operazione di list la gestione viene eseguita tramite TCP. 
Client: invio l’operazione di list al server in attesa di una risposta.
Server: il server riceve la richiesta di vedere tutti i documenti per l’ utente connesso e dopo aver controllato se effettivamente esistono tali documenti, invia al client la risposta contenente, in caso positivo, la stringa con tutti i documenti, i rispettivi collaboratori e il proprietario del documento. 
Client: il client riceve una risposta dal server e in caso di esito positivo stampa la stringa che gli era stata inviata dal server. 
Per eseguire l’operazione di list:
	
	Turing list



  **edit:**
L’operazione di edit viene eseguita anch’essa basandosi sul sistema TCP. Il client prende le informazioni fornite dall‘input e dopo aver controllato che effettivamente siano coerenti le invia al server.
Server: il server esegue vari controlli sui dati forniti dal client. Se tutto è andato a buon fine accede alla cartella precedentemente creata in fase di create e apre il file ( se non è già occupato da qualcuno ) per inviarlo al client (in quanto devo fornire l’ ultima modifica effettuata) dopo il trasferimento del file mando l’ esito di buon fine dell‘operazione. In caso di errore mando il messaggio di errore. 
Client: ricevo la risposta da parte del server. Se l’esito è positivo controllo che esista la cartella dei documenti da editare in caso contrario la creo. Se esisteva già un file con il nome il richiesto dalla edit, lo cancello e ne creo uno nuovo dove andrò ad inserire il file inviato dal server. Se tutto è andato a buon fine avvio il thread che si occupa della comunicazione tra i vari client in modalità editing per un determinato documento.
Per eseguire l’operazione di edit: 
	
	Turing edit [miodocumento] [numerosezione]



  **end-edit:**
Anche l’operazione di end-edit richiede la gestione tramite TCP. Dopo i vari controlli sull’ input il client invia al server le informazioni necessarie per eseguire l’operazione. In seguito, verifico che esista la cartella dedicata ai documenti da editare. In tal caso apro il file passato da input in modalità di lettura e trasferisco (tramite una transferto) la sezione al server. 
Server: il server riceve i dati di input forniti dal client e dopo aver verificato che l’utente sia in modalità editing, verifico se ho passato tutti i controlli. Aggiorno la sezione del documento che ho ricevuto da input e solo in seguito mando l’esito dell‘operazione al client con eventuali messaggi di errore.
Client: se l’operazione è andata a buon fine disattivo la chat creata nella edit precedente.
Per eseguire l’operazione di end-edit
	
	Turing edit [miodocumento] [numerosezione]


  **send:**
L’ operazione send è implementata sfruttando il protocollo UDP. Per la gestione di questa operazione non si passa attraverso il server ma la comunicazione viene fatta sfruttando un apposito thread che viene creato durante la fase di editing. La send genera il pacchetto ( datagrampacket ) che viene inviato all’ indirizzo fornito dal documento per lo scambio dei messaggi. Il delimitatore utilizzato per il messaggio è “_”.
Per eseguire l’operazione di send
	
	Turing send [messaggio]


  **receive:**
L’ operazione receive è basata anch’ essa sul protocollo UDP. Quest’ ultima, come la precedente, non passa attraverso il server ma si basa sul thread che gestisce la chat. Controlla che la lista dei messaggi sia non vuota e per ogni messaggio stampa a schermo il contenuto.
Per eseguire l’operazione di receive 
	
	Turing receive




**Funzionamento:**
Essendo un’applicazione client-server, come qualsiasi altra applicazione, la prima classe ad essere avviata è quella del Server. Esso avvia, come già detto, sia un thread che si occuperà degli inviti online sia un thread per le varie richieste fornite dal client. A questo punto ServerRichieste entra in un ciclo infinito (terminabile solo con la chiusura del server) e gestisce le varie operazioni attraverso un Selector (quindi eseguendo il multiplexing dei vari canali NIO). Il vantaggio del selector sta nel poter registrare i socketchannel come non-bloccanti in quanto le operazioni di lettura e scrittura sono effettuabili solo dopo che il selector notifica il rispettivo evento di lettura/scrittura. Il selector controlla ogni volta se vi è uno dei tre eventi disponibile per i vari socket registrati. Gli eventi sono: op_accept, op_read, op_write.
Op_accept: se sono qui significa che la socket è stata registrata nel selettore e viene accettata
Op_read: se sono qui significa che la socket registrata è pronta per ricevere operazioni in lettura (dal client quindi) e di conseguenza vado a leggere le varie operazioni inviate dal client. Al termine dell’operazione vado a registrare questo channel con l’operazione di op_write allegando le informazioni che poi dovranno essere spedite al client. 
Op_write: se sono qui significa che la socket registrata è pronta per effettuare operazioni in scrittura (verso il client quindi) e di conseguenza estraggo l’allegato della precedente op_read. Dopo aver concluso tutta l’operazione, invio l’esito al client più eventuali files.
Il client viene eseguito in seguito all’avvio del server ed è composto da un solo thread. Come già detto, a differenza del server, le socket utilizzate sono bloccanti in quanto se non ho ricevuto una risposta dal server non posso proseguire nell’operazione. Il client si occupa innanzitutto di verificare i vari dati di input e in caso di errore notifica, a chi deve inserire i dati di input, che i dati inseriti non sono corretti. Le varie operazioni vengono eseguite all’interno di un ciclo infinito dal quale si esce solo dopo aver terminato il client. Se i dati di input sono inseriti correttamente posso proseguire e invio al server i dati con l’operazione usando un delimitatore (“-“). Ogni tipo di operazione può far transitare un utente dallo stato di “register”-“logged”-“editing” così come mostrato dall’automa a stati finiti del progetto.
