import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;


public class Client {
	private static BufferedReader br;
	private static String linea;
	private static String nome = null;
	private static SocketChannel socketchannel = null;
	private static SocketAddress address = null;
	private static InetAddress indirizzoMulticast = null;
	private static int portaChat = 4783;
	private static int portaInviti = 4343;
	private static ChatRoom chroom;
	private static SocketChannel socketFile = null;
	private static int numerosezionemax;
    private static MulticastSocket socketch = null;
	private static ServerUtenteRMI server;
	private static Remote RemoteObject;
	private static int porta = 5555;


    // funzione hash utilizzata per ottenere una porta diversa per ogni utente (serve per lo scambio file client-server)
    private static int FunzioneHash(String nome){
        // la maschera applicata cambia l'intero a 32 bit in un intero non negativo a 31 bit
        int aux = ((nome.hashCode() & 0x7fffffff) % ((int)Math.pow(2,16) - 1));
        if(aux < 1024 ) aux = aux + 1024;
        return aux;
    }


    // verifico il sistema operativo sul quale sto lavorando. Caso in cui sono sotto windows
    private static boolean isWindows(String OS) {

        return (OS.indexOf("win") >= 0);

    }

    // verifico il sistema operativo sul quale sto lavorando. Caso in cui sono sotto un sistema Unix
    private static boolean isUnix(String OS) {

        return (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0 );

    }


    public static void main (String args[]) throws IOException {


		// genero il socketchannel che si connetterà al server per la comunicazione delle varie operazioni client-server
		address = new InetSocketAddress(InetAddress.getLocalHost(),porta);
		socketchannel = SocketChannel.open();
		socketchannel.connect(address);
        InvitiOnlineClient ioc = null;
		br = new BufferedReader(new InputStreamReader(System.in));
		try {
			while(true) {

			    // leggo l'operazione da input con i rispettivi dati e la splitto
				linea = br.readLine();
				String[] arraux = linea.split(" ");
				String operazione = arraux[1];

                // verifico l'operazione che è stata richiesta
				switch(operazione) {

				    // caso in cui  voglio effettuare registrare un utente
                    case "register":

                        if(arraux.length != 4){
                            System.out.println("il numero degli argomenti non è valido");
                            break;
                        }

                        if(arraux[2].isEmpty()){
                            System.out.println("il nome inserito non è valido");
                            break;
                        }

                        if(arraux[3].isEmpty()) {
                            System.out.println("la password inserita non è valida");
                            break;
                        }
                        Utente uregister = ServerRichieste.getUtenteByName(arraux[2]);

                        // se l'utente non esiste allora lo registro tramite gestione RMI
                        if (uregister == null) {
                            // controllare il numero di parametri passati come riferimento
                            try {


                                Utente u = new Utente(arraux[2], arraux[3]);
                                nome = arraux[2];
                                Registry r = LocateRegistry.getRegistry(7000);
                                RemoteObject = r.lookup("S");
                                server = (ServerUtenteRMI) RemoteObject;
                                server.RegistraUtente(nome, u);
                                System.out.println("richiesta di registrazione avvenuta con successo");

                            } catch (NotBoundException e) {
                                System.out.println("richiesta di registrazione non avvenuta con successo");
                                e.printStackTrace();
                            }
                        } else {
                            System.out.println("Impossibile eseguire una register dopo un login");
                        }
                        break;

                        // caso in cui l'operazione richiesta è un'operazione di login
                    case "login":

                        ByteBuffer bytebuffer = ByteBuffer.allocate(4096);

                        if(arraux.length != 4){
                            System.out.println("il numero degli argomenti non è valido");
                            break;
                        }

                        if(arraux[2].isEmpty()){
                            System.out.println("il nome inserito non è valido");
                            break;
                        }

                        if(arraux[3].isEmpty()) {
                            System.out.println("la password inserita non è valida");
                            break;
                        }

                        byte[] nomelogin = arraux[2].getBytes();
                        byte[] password = arraux[3].getBytes();

                        // preparo la richiesta che deve essere inviata al server usando "-" come delimitatore
                        bytebuffer.put("login".getBytes());
                        bytebuffer.put("-".getBytes());
                        bytebuffer.put(nomelogin);
                        bytebuffer.put("-".getBytes());
                        bytebuffer.put(password);

                        String credenziali = new String(bytebuffer.array(), 0, bytebuffer.position());
                        int lunghezzacredenziali = bytebuffer.position();
                        ByteBuffer infocredenziali = ByteBuffer.allocate(4096);
                        infocredenziali.putInt(lunghezzacredenziali);
                        infocredenziali.put(credenziali.getBytes());
                        infocredenziali.flip();

                        //invio la richiesta al server
                        while(infocredenziali.hasRemaining()) {
                            socketchannel.write(infocredenziali);
                        }


                        bytebuffer.clear();
                        ByteBuffer buffer = ByteBuffer.allocate(4096);
                        char esitorisposta = 'n';
                        String rispostalogin = null;
                        String messaggiologin = null;
                        String risplogin = null;
                        String invitioff = null;
                        boolean checklogin = false;

                        StringBuilder builderlogin = new StringBuilder();

                        // leggo la risposta che ho ricevuto dal server
                        int numerolettilogin = socketchannel.read(buffer);
                        int numerodimensionelogin = buffer.flip().getInt() + 4;
                        while(!checklogin){

                            while(buffer.hasRemaining()){
                                builderlogin.append((char)buffer.get());
                                //System.out.println("position: " + buffer.position());
                                //System.out.println("limit: " + buffer.limit());
                            }
                            if(numerolettilogin == numerodimensionelogin){
                                checklogin = true;
                            }
                            else{
                                buffer.clear();
                                numerolettilogin = numerolettilogin + socketchannel.read(buffer);
                            }
                        }

                        // stringa contenente la risposta del server
                        rispostalogin = builderlogin.toString();
                        System.out.println(rispostalogin);
                        // la prima volta sarà sicuramente 'n' e in questo caso prelevo la risposta inviata dal server
                            if (esitorisposta == 'n') {

                                // splitto la risposta in quanto sarà del tipo "info1-info2-infoN-"
                                String[] partirisplogin = rispostalogin.split("-");
                                risplogin = partirisplogin[0];
                                messaggiologin = partirisplogin[1];

                                // invitioff contiene la lista degli inviti ottenuti mentre l'utente era offline
                                if (partirisplogin.length == 3) invitioff = partirisplogin[2];
                                if (risplogin.equals("y"))
                                    esitorisposta = 'y';
                                else esitorisposta = 'n';
                            }
                            // è andato tutto bene
                            if (esitorisposta == 'y') {

                                // creo la socket utilizzata per avviare il thread che si occupa della gestione degli inviti online
                                SocketAddress addrinv = new InetSocketAddress("localhost", portaInviti);
                                SocketChannel socketInviti = SocketChannel.open();
                                socketInviti.connect(addrinv);
                                //InvitiOnlineClient ioc = new InvitiOnlineClient(socketInviti,arraux[2]);
                                ioc = new InvitiOnlineClient(socketInviti,arraux[2]);
                                ioc.start();

                                buffer.clear();
                                System.out.println(messaggiologin);
                                // stampo se ci sono stati inviti mentre l'utente era offline
                                System.out.println("l'utente: " + arraux[2] + " mentre era offline e' stato invitato ai seguenti documenti: " + invitioff);

                                int portaFile = FunzioneHash(arraux[2]);
                                SocketAddress addrfile = new InetSocketAddress("localhost", portaFile);
                                socketFile = SocketChannel.open();
                                socketFile.connect(addrfile);

                                break;
                            }
                            // non ho trovato l'utente cercato
                            else if (esitorisposta == 'n') {
                                System.out.println(messaggiologin);
                            }


                        break;

                    case "create":

                            if(arraux.length != 4){
                                System.out.println("il numero degli argomenti non è valido");
                                break;
                            }

                            if(arraux[2].isEmpty()){
                                System.out.println("il nome inserito per il documento non è valido");
                                break;
                            }

                            if(arraux[3].isEmpty()) {
                                System.out.println("non è stato inserito il numero di sezioni");
                                break;
                            }

                            ByteBuffer bytebuffer1 = ByteBuffer.allocate(4096);
                            byte[] nomeDocumento = arraux[2].getBytes();
                            byte[] numerosezioni = arraux[3].getBytes();
                            int numerosezaux = 0;
                            try{
                                numerosezaux = Integer.parseInt(arraux[3]);
                            }
                            catch(NumberFormatException e){
                                System.out.println("non è stato inserito un numero come numero di sezione");
                                break;
                            }
                            String nomedocaux = arraux[2];
                            if (numerosezaux == 0 || numerosezaux < 0) {
                                System.out.println("il numero della sezione non è corretto");
                                break;
                                //messaggio di errore
                            } else {

                                // preparo la richiesta da inviare al server
                                numerosezionemax = numerosezaux;
                                bytebuffer1.put("create".getBytes());
                                bytebuffer1.put("-".getBytes());
                                bytebuffer1.put(nomeDocumento);
                                bytebuffer1.put("-".getBytes());
                                bytebuffer1.put(numerosezioni);

                                String nuovoDoc = new String(bytebuffer1.array(), 0, bytebuffer1.position());
                                int lunghezzaInfoDoc = bytebuffer1.position();
                                ByteBuffer infoDoc = ByteBuffer.allocate(4096);
                                infoDoc.putInt(lunghezzaInfoDoc);
                                infoDoc.put(nuovoDoc.getBytes());
                                infoDoc.flip();


                                // invio al server la richiesta
                                while(infoDoc.hasRemaining()) {
                                    socketchannel.write(infoDoc);
                                }

                                bytebuffer1.clear();
                                ByteBuffer buffer1 = ByteBuffer.allocate(4096);
                                char esitorispostacreate = 'n';

                                boolean checkcreate = false;
                                String rispostacreate = null;
                                String messaggiocreate = null;
                                String rispcreate = null;
                                StringBuilder buildercreate = new StringBuilder();

                                // ottengo la risposta dal server
                                int numeroletticreate = socketchannel.read(buffer1);
                                int numerodimensionecreate = buffer1.flip().getInt() + 4;
                                while(!checkcreate){

                                    while(buffer1.hasRemaining()){

                                        buildercreate.append((char)buffer1.get());
                                        //System.out.println("position: " + buffer1.position());
                                        //System.out.println("limit: " + buffer1.limit());
                                    }
                                    if(numeroletticreate == numerodimensionecreate){
                                        checkcreate = true;
                                    }
                                    else{
                                        buffer1.clear();
                                        numeroletticreate = numeroletticreate + socketchannel.read(buffer1);
                                    }
                                }

                                // stringa contenente la risposta del server
                                rispostacreate = buildercreate.toString();
                                    // la prima volta sarà sicuramente 'n' e in questo caso prelevo la risposta inviata dal server
                                    if (esitorispostacreate == 'n') {

                                        String[] partirispcreate = rispostacreate.split("-");
                                        rispcreate = partirispcreate[0];
                                        messaggiocreate = partirispcreate[1];
                                        if (rispcreate.equals("y"))
                                            esitorispostacreate = 'y';
                                        else esitorispostacreate = 'n';

                                    }
                                    // è andato tutto bene
                                    if (esitorispostacreate == 'y') {

                                        System.out.println(messaggiocreate);
                                    }
                                    // non ho trovato l'utente cercato
                                    else if (esitorispostacreate == 'n') {
                                        System.out.println(messaggiocreate);
                                    }

                            }


                        break;

                    case "share":

                            if(arraux.length != 4){
                                System.out.println("il numero degli argomenti non è valido");
                                break;
                            }

                            if(arraux[2].isEmpty()){
                                System.out.println("il nome inserito per il documento non è valido");
                                break;
                            }

                            if(arraux[3].isEmpty()) {
                                System.out.println("il nome dell'invitato non è valido");
                                break;
                            }

                            ByteBuffer bytebuffershare = ByteBuffer.allocate(4096);
                            byte[] nomeDocumentoshare = arraux[2].getBytes();
                            byte[] nomeInvitato = arraux[3].getBytes();

                            //preparo l'operazione da inviare al server
                            bytebuffershare.put("share".getBytes());
                            bytebuffershare.put("-".getBytes());
                            bytebuffershare.put(nomeDocumentoshare);
                            bytebuffershare.put("-".getBytes());
                            bytebuffershare.put(nomeInvitato);

                            String ShareDoc = new String(bytebuffershare.array(), 0, bytebuffershare.position());
                            int lunghezzaInfoDoc = bytebuffershare.position();
                            ByteBuffer infoDoc = ByteBuffer.allocate(4096);
                            infoDoc.putInt(lunghezzaInfoDoc);
                            infoDoc.put(ShareDoc.getBytes());
                            infoDoc.flip();

                            // mando l'operazione al server
                            while(infoDoc.hasRemaining()) {
                                socketchannel.write(infoDoc);
                            }

                            bytebuffershare.clear();
                            ByteBuffer buffer1 = ByteBuffer.allocate(4096);
                            char esitorispostashare = 'n';
                            int numeroshare;
                            String rispostashare = null;
                            String messaggioshare = null;
                            String rispshare = null;
                            StringBuilder buildershare = new StringBuilder();
                            boolean checkshare = false;

                            // ottengo la risposta dal server
                            int numerolettishare = socketchannel.read(buffer1);
                            int numerodimensioneshare = buffer1.flip().getInt() + 4;
                            while(!checkshare){

                                while(buffer1.hasRemaining()){
                                    buildershare.append((char)buffer1.get());
                                    //System.out.println("position: " + buffer1.position());
                                    //System.out.println("limit: " + buffer1.limit());
                                }
                                if(numerolettishare == numerodimensioneshare){
                                    checkshare = true;
                                }
                                else{
                                    buffer1.clear();
                                    numerolettishare = numerolettishare + socketchannel.read(buffer1);
                                }
                            }

                            // salvo la risposta in una stringa
                            rispostashare = buildershare.toString();
                                // la prima volta sarà sicuramente 'n' e in questo caso prelevo la risposta inviata dal server
                                if (esitorispostashare == 'n') {

                                    String[] partirispcreate = rispostashare.split("-");
                                    rispshare = partirispcreate[0];
                                    messaggioshare = partirispcreate[1];
                                    if (rispshare.equals("y"))
                                        esitorispostashare = 'y';
                                    else esitorispostashare = 'n';
                                }
                                // è andato tutto bene
                                if (esitorispostashare == 'y') {

                                    System.out.println(messaggioshare);
                                }
                                // non ho trovato l'utente cercato
                                else if (esitorispostashare == 'n') {
                                    System.out.println(messaggioshare);
                                }


                        break;


                    case "show":

                            if(arraux.length != 4 && arraux.length != 3){
                                System.out.println("il numero degli argomenti non è valido");
                                break;
                            }

                            if(arraux[2].isEmpty()){
                                System.out.println("il nome inserito per il documento non è valido");
                                break;
                            }

                            byte[] nomeDocumentoShow = null;
                            byte[] numeroSezioneShow = null;
                            int auxnumsezshow = 0;
                            String nomedocshowaux = arraux[2];
                            FileChannel fileoutshow;

                            // faccio questo controllo in quanto se ho il numero di sezione significa che voglio visualizzare
                            // una sezione del documento altrimenti vuol dire che voglio visualizzare l'intero documento
                            // e di conseguenza utilizzo "_1" come carattere speciale per indicare che voglio visualizzare
                            // l'intero documento
                            if (arraux.length == 4) {
                                nomeDocumentoShow = arraux[2].getBytes();
                                try {
                                    auxnumsezshow = Integer.parseInt(arraux[3]);
                                }
                                catch( NumberFormatException e){
                                    System.out.println("non è stata inserito un numero per indicare la sezione");
                                    break;
                                }
                                numeroSezioneShow = arraux[3].getBytes();
                            }
                            else if (arraux.length == 3) {
                                nomeDocumentoShow = arraux[2].getBytes();
                                numeroSezioneShow = "_1".getBytes();
                                auxnumsezshow = -1;
                            }


                            ByteBuffer bytebuffershow = ByteBuffer.allocate(4096);

                            // preparo la richiesta da inviare al server
                            bytebuffershow.put("show".getBytes());
                            bytebuffershow.put("-".getBytes());
                            bytebuffershow.put(nomeDocumentoShow);
                            bytebuffershow.put("-".getBytes());
                            bytebuffershow.put(numeroSezioneShow);
                            String opShow = new String(bytebuffershow.array(), 0, bytebuffershow.position());
                            int lunghezzaInfoShow = bytebuffershow.position();
                            ByteBuffer infoShow = ByteBuffer.allocate(4096);
                            infoShow.putInt(lunghezzaInfoShow);
                            infoShow.put(opShow.getBytes());
                            infoShow.flip();

                        // invio la richiesta al server
                            while(infoShow.hasRemaining()) {
                                socketchannel.write(infoShow);
                            }


                            boolean tuttook = false;
                            char esitorispostashow = 'n';
                            int numeroshow;
                            String rispostashow = null;
                            String messaggioshow = null;
                            String rispshow = null;
                            ByteBuffer buffershow2 = ByteBuffer.allocate(4096);
                            String stringashow = null;
                            StringBuilder buildershow = new StringBuilder();

                            boolean checkshow = false;

                            // ricevo la risposta dal server su tale operazione
                            int numerolettishow = socketchannel.read(buffershow2);
                            int numerodimensioneshow = buffershow2.flip().getInt() + 4;
                            while(!checkshow){

                                while(buffershow2.hasRemaining()){
                                    buildershow.append((char)buffershow2.get());
                                    //System.out.println("position: " + buffershow2.position());
                                    //System.out.println("limit: " + buffershow2.limit());
                                }
                                if(numerolettishow == numerodimensioneshow){
                                    checkshow = true;
                                }
                                else{
                                    buffershow2.clear();
                                    numerolettishow = numerolettishow + socketchannel.read(buffershow2);
                                }
                            }

                            // trasformo la risposta ricevuta in stringa
                            rispostashow = buildershow.toString();
                            if(esitorispostashow == 'n'){
                                String[] partirispedit = rispostashow.split("-");
                                rispshow = partirispedit[0];
                                messaggioshow = partirispedit[1];

                                if(rispshow.equals("y")){
                                    esitorispostashow = 'y';
                                    tuttook = true;
                                }
                                else {
                                    esitorispostashow = 'n';
                                    tuttook = false;
                                }
                            }
                            // è andato tutto bene
                            if (esitorispostashow == 'y'){

                                System.out.println(messaggioshow);
                            }
                            // non ho trovato l'utente cercato
                            else if (esitorispostashow == 'n'){
                                System.out.println(messaggioshow);
                            }

                            // se sono qui significa che il server ha passato i controlli dunque mi preparo a ricevere il
                            // documento/ sezione
                            if(tuttook == true) {
                                //Download del File
                                // devo controllare se esiste la cartella
                                String os = System.getProperty("os.name").toLowerCase();
                                String tempfolder = System.getProperty("java.io.tmpdir");
                                if (isWindows(os)) tempfolder = tempfolder + "\\DocumentiDaScaricare\\";
                                if (isUnix(os)) tempfolder = tempfolder + "/DocumentiDaScaricare/";
                                System.out.println("la cartella per l'edit è: " + tempfolder + nome);
                                Path path = Paths.get(tempfolder + nome);


                                // se non esiste la cartella "documentidascaricare" la creo
                                if (!Files.exists(Paths.get(tempfolder))) Files.createDirectory(Paths.get(tempfolder));
                                //se non esiste la cartella "documentidascaricare/nomeutente/" la creo
                                if (!Files.exists(path)) Files.createDirectory(path);

                                Path filepath;

                                // caso in cui voglio tutto il documento salvo il path
                                if (auxnumsezshow == -1) {

                                    filepath = Paths.get(tempfolder + nome + "/" + nomedocshowaux + "-" + "Tutto.txt");
                                    //se non esiste lo creo
                                    if (!Files.exists(filepath)) {
                                        Files.createFile(filepath);
                                        System.out.println("percorso file create con successo");
                                    }
                                } else {

                                    // caso in cui voglio una sola sezione salvo il path
                                    filepath = Paths.get(tempfolder + nome + "/" + nomedocshowaux + "-" + auxnumsezshow + ".txt");
                                    //se non esiste lo creo
                                    if (!Files.exists(filepath)) Files.createFile(filepath);
                                }

                                //se non esiste il documento lo creo
                                if (!Files.exists(filepath)) Files.createFile(filepath);
                                else {
                                    //se ho un file più vecchio lo cancello e lo ricreo
                                    Files.delete(filepath);
                                    Files.createFile(filepath);
                                }

                                // apro il file che conterrà la concatenazione di tutte le sezioni
                                if (auxnumsezshow == -1) {
                                    fileoutshow = FileChannel.open(Paths.get(tempfolder + nome + "/" +nomedocshowaux + "-" + "Tutto.txt"), StandardOpenOption.WRITE);

                                } else {
                                    // apro il file che conterrà la sezione di un file
                                    fileoutshow = FileChannel.open(Paths.get(tempfolder + nome + "/" + nomedocshowaux + "-" + auxnumsezshow + ".txt"), StandardOpenOption.WRITE);

                                }

                                ByteBuffer buffershow = ByteBuffer.allocateDirect(2048);


                                // avvio lo scambio del file dal server verso il client
                                int letti;
                                while ((letti = socketFile.read(buffershow)) != -1) {
                                    buffershow.flip();
                                    while (buffershow.hasRemaining())
                                        fileoutshow.write(buffershow);
                                    buffershow.clear();
                                }
                                fileoutshow.close();
                                socketFile.close();

                                // ricreo il collegamento per lo scambio del file precedentemente chiuso in quando la read restituisce -1 solo in caso di EOF
                                // ovvero quando chiudo la socket
                                int portaFile = FunzioneHash(nome);
                                SocketAddress addrfile = new InetSocketAddress("localhost", portaFile);
                                socketFile = SocketChannel.open();
                                socketFile.connect(addrfile);

                                esitorispostashow = 'n';
                                numeroshow = 0;
                                rispostashow = null;
                                messaggioshow = null;
                                rispshow = null;
                                ByteBuffer buffershow3 = ByteBuffer.allocate(4096);
                                stringashow = null;
                                String messshow = null;
                                StringBuilder buildershow1 = new StringBuilder();

                                // dopo il trasferimento ricevo la risposta finale da parte del server per aver concluso il trasferimento
                                boolean checkshow1 = false;
                                int numerolettishow1 = socketchannel.read(buffershow3);
                                int numerodimensioneshow1 = buffershow3.flip().getInt() + 4;
                                while(!checkshow1){
                                    while(buffershow3.hasRemaining()){
                                        buildershow1.append((char)buffershow3.get());
                                        //System.out.println("position: " + buffershow3.position());
                                        //System.out.println("limit: " + buffershow3.limit());
                                    }
                                    if(numerolettishow1 == numerodimensioneshow1){
                                        checkshow1 = true;
                                    }
                                    else{
                                        buffershow3.clear();
                                        numerolettishow1 = numerolettishow1 + socketchannel.read(buffershow3);
                                    }
                                }
                                rispostashow = buildershow1.toString();
                                    // la prima volta sarà sicuramente 'n' e in questo caso prelevo la risposta inviata dal server
                                    if (esitorispostashow == 'n') {
                                        String[] partirisplist = rispostashow.split("-");
                                        rispshow = partirisplist[0];
                                        messaggioshow = partirisplist[1];
                                        if (rispshow.equals("y")) {
                                            esitorispostashow = 'y';
                                            // stringashow avrà il nome del documento
                                            stringashow = partirisplist[2];
                                            // messshow avrà la stringa contenente le sezioni sotto modifica
                                            messshow = partirisplist[3];
                                        } else esitorispostashow = 'n';

                                    }
                                    // è andato tutto bene
                                    if (esitorispostashow == 'y') {
                                        // creo la stringa
                                        System.out.println(messaggioshow);
                                        System.out.println(stringashow);
                                        System.out.println(messshow);
                                    }
                                    // non ho trovato l'utente cercato
                                    else if (esitorispostashow == 'n') {
                                        System.out.println(messaggioshow);
                                    }


                            }


						break;


					case "list":

					    // caso in cui l'operazione richiesta è una list

                            ByteBuffer bytebufferlist = ByteBuffer.allocate(4096);
                            bytebufferlist.put("list".getBytes());

                            String nuovoMessList = new String(bytebufferlist.array(), 0, bytebufferlist.position());
                            int lunghezzaInfolist = bytebufferlist.position();
                            ByteBuffer infolist = ByteBuffer.allocate(4096);
                            infolist.putInt(lunghezzaInfolist);
                            infolist.put(nuovoMessList.getBytes());
                            infolist.flip();

                            //mando la richiesta al server
                            while(infolist.hasRemaining()) {
                                socketchannel.write(infolist);
                            }

                            bytebufferlist.clear();
                            ByteBuffer bufferlist = ByteBuffer.allocate(4096);
                            char esitorispostalist = 'n';
                            int numerolist;
                            String rispostalist = null;
                            String messaggiolist = null;
                            String risplist = null;
                            String StringaFinale = null;
                            StringBuilder builderlist = new StringBuilder();
                            boolean checklist = false;

                            // leggo la risposta del server
                            int numerolettilist = socketchannel.read(bufferlist);
                            int numerodimensionelist = bufferlist.flip().getInt() + 4;
                            while(!checklist){
                                //buffer.flip();
                                while(bufferlist.hasRemaining()){
                                    builderlist.append((char)bufferlist.get());
                                    //System.out.println("position: " + bufferlist.position());
                                    //System.out.println("limit: " + bufferlist.limit());
                                }
                                if(numerolettilist == numerodimensionelist){
                                    checklist = true;
                                }
                                else{
                                    bufferlist.clear();
                                    numerolettilist = numerolettilist + socketchannel.read(bufferlist);
                                }
                            }


                            // salvo la risposta del server in una stringa
                            rispostalist = builderlist.toString();
                                // la prima volta sarà sicuramente 'n' e in questo caso prelevo la risposta inviata dal server
                                if (esitorispostalist == 'n') {

                                    String[] partirisplist = rispostalist.split("-");
                                    risplist = partirisplist[0];
                                    messaggiolist = partirisplist[1];
                                    if(risplist.equals("y")) {
                                        esitorispostalist = 'y';
                                        // stringaFinale contiene l'elenco dei documenti con i rispettivi collaboratori e proprietari
                                        StringaFinale = partirisplist[2];
                                    }
                                    else esitorispostalist = 'n';

                                }
                                // è andato tutto bene
                                if (esitorispostalist == 'y') {

                                    System.out.println(messaggiolist);

                                }
                                // non ho trovato l'utente cercato
                                else if (esitorispostalist == 'n') {
                                    System.out.println(messaggiolist);
                                }

                                if(esitorispostalist == 'y') {
                                    // se l'utente ha documenti di cui è creatore o collaboratore li stampo altrimenti stampo un messaggio
                                    // di errore
                                    if (!StringaFinale.isEmpty() && StringaFinale != null)
                                        System.out.println("Documenti per l'utente " + nome + ": " + StringaFinale);
                                    else {
                                        System.out.println("Nessun documento è presente per il seguente utente " + nome);
                                    }
                                }




                                break;


					case "edit":

                        if(arraux.length != 4){
                            System.out.println("il numero degli argomenti non è valido");
                            break;
                        }

                        if(arraux[2].isEmpty()){
                            System.out.println("il nome inserito per il documento non è valido");
                            break;
                        }

                        if(arraux[3].isEmpty()) {
                            System.out.println("non è stato inserito il numero corretto per le sezioni");
                            break;
                        }
					    // se l'operazione richiesta è l'operazione di edit
                        boolean tutti = false;
                        ByteBuffer bytebufferedit = ByteBuffer.allocate(4096);
                        byte[] nomeDocumentoEdit = arraux[2].getBytes();
                        byte[] numeroSezioneEdit = arraux[3].getBytes();
                        int auxnumsezedit = 0;
                        try {
                            auxnumsezedit = Integer.parseInt(arraux[3]);
                        }
                        catch(NumberFormatException e){
                            System.out.println("il campo sezione non presenta un numero");
                            break;
                        }
                        String nomedoceditaux = arraux[2];

                        if( auxnumsezedit <= 0  && auxnumsezedit > numerosezionemax){
                            System.out.println("il numero per la sezione non è corretto, esce fuori dal range");
                            break;
                        }

                        // preparo l'operazione da inviare al server
                        bytebufferedit.put("edit".getBytes());
                        bytebufferedit.put("-".getBytes());
                        bytebufferedit.put(nomeDocumentoEdit);
                        bytebufferedit.put("-".getBytes());
                        bytebufferedit.put(numeroSezioneEdit);
                        String infoEdit = new String(bytebufferedit.array(), 0, bytebufferedit.position());
                        int lunghezzaInfoEdit = bytebufferedit.position();
                        ByteBuffer infoEditMess = ByteBuffer.allocate(4096);

                        infoEditMess.putInt(lunghezzaInfoEdit);
                        infoEditMess.put(infoEdit.getBytes());
                        infoEditMess.flip();


                        // invio l'operazione al server
                        while(infoEditMess.hasRemaining()) {
                            socketchannel.write(infoEditMess);
                        }

                        bytebufferedit.clear();
                        ByteBuffer bufferedit = ByteBuffer.allocate(4096);
                        char esitorispostaedit = 'n';
                        String indirizzoGruppo = null;
                        int numeroedit;
                        StringBuilder builderedit = new StringBuilder();
                        String rispostaedit = null;
                        String rispedit = null;
                        String messaggioedit = null;


                        //ricevo la risposta dal server
                        boolean checkedit = false;
                        int numerolettiedit = socketchannel.read(bufferedit);
                        int numerodimensioneedit = bufferedit.flip().getInt() + 4;
                        while(!checkedit){

                            while(bufferedit.hasRemaining()){
                                builderedit.append((char)bufferedit.get());
                                //System.out.println("position: " + bufferedit.position());
                                //System.out.println("limit: " + bufferedit.limit());
                            }
                            if(numerolettiedit == numerodimensioneedit){
                                checkedit = true;
                            }
                            else{
                                bufferedit.clear();
                                numerolettiedit = numerolettiedit + socketchannel.read(bufferedit);
                            }
                        }


                        // salvo sotto forma di stringa la risposta del server
                        rispostaedit = builderedit.toString();
                            // la prima volta sarà sicuramente 'n' e in questo caso prelevo la risposta inviata dal server
                            if(esitorispostaedit == 'n'){
                                String[] partirispedit = rispostaedit.split("-");
                                rispedit = partirispedit[0];
                                messaggioedit = partirispedit[1];
                                //indirizzoGruppo = new String(bufferedit.array());
                                if(rispedit.equals("y")){
                                    esitorispostaedit = 'y';
                                    // indirizzoGruppo contiene l'indirizzo utilizzato per la chat (operazioni send e receive)
                                    indirizzoGruppo = partirispedit[2];
                                    // devo levare il carattere "/" dall'indirizzo
                                    indirizzoGruppo = indirizzoGruppo.replace("/","");
                                }
                                else esitorispostaedit = 'n';
                            }
                            // è andato tutto bene
                            if (esitorispostaedit == 'y'){

                                System.out.println(messaggioedit);
                            }
                            // non ho trovato l'utente cercato
                            else if (esitorispostaedit == 'n'){
                                System.out.println(messaggioedit);
                            }

                            // se è andato tutto bene aspetto dal server il file che ho chiesto
                            if(esitorispostaedit == 'y') {
                                // devo controllare se esiste la cartella
                                String os = System.getProperty("os.name").toLowerCase();
                                String tempfolder = System.getProperty("java.io.tmpdir");
                                // creo la cartella dove ho i documenti da editare
                                if (isWindows(os)) tempfolder = tempfolder + "\\DocumentiDaEditare\\";
                                if (isUnix(os)) tempfolder = tempfolder + "/DocumentiDaEditare/";
                                if (!Files.exists(Paths.get(tempfolder))) Files.createDirectory(Paths.get(tempfolder));
                                System.out.println("la cartella per l'edit è: " + tempfolder + nome);
                                Path path = Paths.get(tempfolder + nome);

                                //se non esiste la creo
                                if (!Files.exists(path)) Files.createDirectory(path);

                                //devo controllare ora se esiste il file
                                System.out.println(tempfolder + nome + "/" + nomedoceditaux + "-" + auxnumsezedit + ".txt");
                                Path filepath = Paths.get(tempfolder + nome + "/" + nomedoceditaux + "-" + auxnumsezedit + ".txt");
                                //se non esiste lo creo
                                if (!Files.exists(filepath)) Files.createFile(filepath);
                                else {

                                    Files.delete(filepath);
                                    Files.createFile(filepath);
                                }

                                // apro effettivamente il file
                                FileChannel fileoutedit = FileChannel.open(Paths.get(tempfolder + nome + "/" + nomedoceditaux + "-" + auxnumsezedit + ".txt"), StandardOpenOption.WRITE);

                                // copio tutto il contenuto del file
                                ByteBuffer auxbuf = ByteBuffer.allocate(2048);
                                int letti;
                                while ((letti = socketFile.read(auxbuf)) != -1) {

                                    auxbuf.flip();
                                    while (auxbuf.hasRemaining())
                                        fileoutedit.write(auxbuf);
                                    auxbuf.clear();
                                }
                                fileoutedit.close();
                                socketFile.close();
                                // avvio il thread che si occupa di gestire la chat (operazioni di send-receive)
                                indirizzoMulticast = InetAddress.getByName(indirizzoGruppo);
                                socketch = new MulticastSocket(portaChat);
                                chroom = new ChatRoom(nome,indirizzoMulticast,socketch,portaChat);
                                chroom.start();


                                int portaFile = FunzioneHash(nome);
                                SocketAddress addrfile = new InetSocketAddress("localhost", portaFile);
                                socketFile = SocketChannel.open();
                                socketFile.connect(addrfile);
                                System.out.println(messaggioedit);

                            }


						break;



					case "send":
					        // caso in cui l'utente attuale è in modalità editing e vuole
                            // inviare un messaggio per chi sta editando una sezione del medesiomo documento

                            if(arraux.length != 3){
                                System.out.println("il numero degli argomenti non è valido");
                                break;
                            }

                            if(arraux[2].isEmpty()){
                                System.out.println("il messaggio non è valido");
                                break;
                            }



                            // leggo il contenuto del messaggio
                            String messaggio = arraux[2];

                            //salvo quando è stato inviato
                            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
                            LocalDateTime now = LocalDateTime.now();


                            String nuovomessaggio = nome + " " + dtf.format(now) + " " + messaggio;
                            byte[] messaggioinbyte = nuovomessaggio.getBytes();
                            // creo il messaggio che verrà spedito al thread che si occupa della chat
                            DatagramPacket pacchetto = new DatagramPacket(messaggioinbyte, messaggioinbyte.length, indirizzoMulticast, portaChat);
                            socketch.send(pacchetto);




						break;

					case "receive":
                        // caso in cui l'utente attuale è in modalità editing e vuole
                        // ricevere i messaggi inviati fino a questo momento dagli altri utenti che condividono l'editing
                        // del medesimo documento

                        // estraggo la coda dei messaggi e li stampo

                        LinkedBlockingQueue<String> MessaggiChat = chroom.EstraiMessaggiChat();
                        if (!MessaggiChat.isEmpty()) {
                            Iterator<String> iter = MessaggiChat.iterator();
                            while (iter.hasNext()) {
                                System.out.println(iter.next());
                            }
                        }






						break;

					case "end-edit":


                            // caso in cui la richiesta è una end-edit
                            if(arraux.length != 4){
                                System.out.println("il numero degli argomenti non è valido");
                                break;
                            }

                            if(arraux[2].isEmpty()){
                                System.out.println("il nome inserito per il documento non è valido");
                                break;
                            }

                            if(arraux[3].isEmpty()) {
                                System.out.println("non è stato inserito il numero di sezioni");
                                break;
                            }


                            boolean tuttiendedit = false;
                            ByteBuffer bytebufferendedit = ByteBuffer.allocate(4096);
                            byte[] nomeDocumentoendEdit = arraux[2].getBytes();
                            byte[] numeroSezioneendEdit = arraux[3].getBytes();
                            int auxnumsezendedit = 0;
                            try {
                                auxnumsezendedit = Integer.parseInt(arraux[3]);
                            }
                            catch(NumberFormatException e){
                                System.out.println("il campo sezione non presenta un numero");
                                break;
                            }
                            String nomeDocEndEditAux = arraux[2];
                            if( auxnumsezendedit <= 0 && auxnumsezendedit > numerosezionemax){
                                System.out.println("il campo sezione presenta un numero fuori dal range");
                                break;
                            }

                            // devo controllare se esiste la cartella
                            String os = System.getProperty("os.name").toLowerCase();
                            String tempfolder = System.getProperty("java.io.tmpdir");
                            if (isWindows(os)) tempfolder = tempfolder + "\\DocumentiDaEditare\\";
                            if (isUnix(os)) tempfolder = tempfolder + "/DocumentiDaEditare/";
                            System.out.println("la cartella per l'edit è: " + tempfolder + nome);

                            System.out.println(tempfolder + nome + "/" + nomeDocEndEditAux + "-" + auxnumsezendedit + ".txt");
                            //apro il file scaricato nella cartella in modalità lettura
                            Path percorso = Paths.get(tempfolder + nome + "/" + nomeDocEndEditAux + "-" + auxnumsezendedit + ".txt");
                            FileChannel fileoutedit = null;
                            if(Files.exists(percorso))
                                fileoutedit = FileChannel.open(percorso, StandardOpenOption.READ);
                            else{
                                System.out.println("il percorso contenente il file non è stato ancora creato");
                                break;
                            }

                            // preparo il messaggio da inviare al server
                            bytebufferendedit.put("endedit".getBytes());
                            bytebufferendedit.put("-".getBytes());
                            bytebufferendedit.put(nomeDocumentoendEdit);
                            bytebufferendedit.put("-".getBytes());
                            bytebufferendedit.put(numeroSezioneendEdit);
                            String infoEndEdit = new String(bytebufferendedit.array(), 0, bytebufferendedit.position());
                            int lunghezzaInfoendEdit = bytebufferendedit.position();
                            ByteBuffer infoEndEditMess = ByteBuffer.allocate(4096);
                            infoEndEditMess.putInt(lunghezzaInfoendEdit);
                            infoEndEditMess.put(infoEndEdit.getBytes());
                            infoEndEditMess.flip();

                            // invio la richiesta al server
                            while(infoEndEditMess.hasRemaining()) {
                                socketchannel.write(infoEndEditMess);
                            }

                            // devo inviare il file editato al server in modo che possa salvare le modifiche effettuate(basta dichiarare fuori il path e controllare che esista)
                            if(nome!=null) {

                                ByteBuffer auxbuf = ByteBuffer.allocate(2048);
                                int letti;
                                boolean tuttiletti = false;

                                // invio effettivamente il file
                                long transfered = 0;
                                while (tuttiletti == false) {
                                    transfered = fileoutedit.transferTo(fileoutedit.position(), fileoutedit.size() - fileoutedit.position(), socketFile);
                                    //se ho trasferito meno bytes di quanto sia effettivamente la dimensione del file devo continuare a
                                    //spedire i rimanenti bytes.
                                    fileoutedit.position(fileoutedit.position() + transfered);
                                    if (fileoutedit.position() == fileoutedit.size()) {
                                        tuttiletti = true;
                                        System.out.println("sono stati spediti: " + fileoutedit.position() + " byte su: " + fileoutedit.size());
                                        socketFile.close();
                                        fileoutedit.close();
                                    }
                                }
                                // aspetto una risposta dal server per capire se il trasferimento del file è andato a buon fine
                            }
                                bytebufferendedit.clear();
                                ByteBuffer bufferendedit = ByteBuffer.allocate(4096);
                                String rispostaendedit = null;
                                String messaggioendedit = null;
                                String rispendedit = null;
                                char esitorispostaendedit = 'n';
                                int numeroendedit;
                                StringBuilder builderendedit = new StringBuilder();


                                boolean checkendedit = false;
                                // risposta dal server che mi notifica se tutto è andato bene
                                int numerolettiendedit = socketchannel.read(bufferendedit);
                                int numerodimensioneendedit = bufferendedit.flip().getInt() + 4;
                                while (!checkendedit) {
                                    //buffer.flip();
                                    while (bufferendedit.hasRemaining()) {
                                        builderendedit.append((char) bufferendedit.get());
                                        //System.out.println("position: " + bufferendedit.position());
                                        //System.out.println("limit: " + bufferendedit.limit());
                                    }
                                    if (numerolettiendedit == numerodimensioneendedit) {
                                        checkendedit = true;
                                    } else {
                                        bufferendedit.clear();
                                        numerolettiendedit = numerolettiendedit + socketchannel.read(bufferendedit);
                                    }
                                }



                                rispostaendedit = builderendedit.toString();

                                // la prima volta sarà sicuramente 'n' e in questo caso prelevo la risposta inviata dal server
                                if (esitorispostaendedit == 'n') {
                                    String[] partirispcreate = rispostaendedit.split("-");
                                    rispendedit = partirispcreate[0];
                                    messaggioendedit = partirispcreate[1];
                                    if (rispendedit.equals("y"))
                                        esitorispostaendedit = 'y';
                                    else esitorispostaendedit = 'n';


                                }
                                // è andato tutto bene ricreo la socket per il trasferimento dei file
                                if (esitorispostaendedit == 'y') {
                                    bufferendedit.clear();
                                    int portaFile = FunzioneHash(nome);
                                    SocketAddress addrfile = new InetSocketAddress("localhost", portaFile);
                                    socketFile = SocketChannel.open();
                                    socketFile.connect(addrfile);
                                    //disattivo la chat attivata precedentemente nella "edit"
                                    chroom.disattivaChat();
                                    System.out.println(messaggioendedit);
                                }
                                // non ho trovato l'utente cercato
                                else if (esitorispostaendedit == 'n') {
                                    System.out.println(messaggioendedit);
                                }





						break;

					case "logout":
					    // se la richiesta è di logout

							System.out.println("logout");
							ByteBuffer bytebuffer3 = ByteBuffer.allocate(4096);
							bytebuffer3.put("logout".getBytes());

							String slogout = new String(bytebuffer3.array(),0,bytebuffer3.position());
							int lunghezzaInfo = bytebuffer3.position();
							ByteBuffer infologout = ByteBuffer.allocate(4096);

							// preparo la richiesta da inviare al server
							infologout.putInt(lunghezzaInfo);
							infologout.put(slogout.getBytes());
							infologout.flip();
							// la invio
                            while(infologout.hasRemaining()) {
                                socketchannel.write(infologout);
                            }

							ByteBuffer bufferlogout = ByteBuffer.allocate(4096);
							char esitorispostalogout = 'n';
							int numerologout;
                            String rispostalogout = null;
                            String messaggiologout = null;
                            String risplogout = null;
                            StringBuilder builderlogout = new StringBuilder();


                            boolean checklogout = false;

                            // leggo la risposta inviata dal server
                            int numerolettilogout = socketchannel.read(bufferlogout);
                            int numerodimensionelogout = bufferlogout.flip().getInt() + 4;
                            while(!checklogout){

                                while(bufferlogout.hasRemaining()){
                                    builderlogout.append((char)bufferlogout.get());
                                    //System.out.println("position: " + bufferlogout.position());
                                    //System.out.println("limit: " + bufferlogout.limit());
                                }
                                if(numerolettilogout == numerodimensionelogout){
                                    checklogout = true;
                                }
                                else{
                                    bufferlogout.clear();
                                    numerolettilogout = numerolettilogout + socketchannel.read(bufferlogout);
                                }
                            }

                            rispostalogout = builderlogout.toString();
								// la prima volta sarà sicuramente 'n' e in questo caso prelevo la risposta inviata dal server
								if(esitorispostalogout == 'n'){
                                    String[] partirispcreate = rispostalogout.split("-");
                                    risplogout = partirispcreate[0];
                                    messaggiologout = partirispcreate[1];
                                    if(risplogout.equals("y"))
                                        esitorispostalogout = 'y';
                                    else esitorispostalogout = 'n';

								}
								// è andato tutto bene
								if (esitorispostalogout == 'y'){

									//bufferlogout.clear();
                                    socketFile.close();
                                    //socketchannel.close();
                                    ioc.QuitInviti();

									System.out.println(messaggiologout);
									break;
								}
								// non ho trovato l'utente cercato
								if (esitorispostalogout == 'n'){
                                    System.out.println(messaggiologout);
								}


						break;

					case "--help":
						use();

                    default:
                        System.out.println("l'operazione inserita non esiste, reinserire l'operazione");
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("programma terminato correttamente");
	}

	private static void use() {
		System.out.printf("usage: turing COMMAND [ARGS..]\n"
				+ "\n"
				+ "commands:\n"
				+ "register <username> <password> registra utente\n"
				+ "login <username> <password> effettua il login\n"
				+ "logout effettua il logout\n"
				+ "\n"
				+ "create <doc> <numsezioni> crea un documento\n"
				+ "share <doc> <username> condivide il documento\n"
				+ "show <doc> <sec> mostra una sezione del documento\n"
				+ "list mostra la lista dei documenti\n"
				+ "\n"
				+ "edit <doc> <sec> modifica una sezione del documento\n"
				+ "end-edit <doc> <sec> fine modifica della sezione del doc.\n"
				+ "\n"
				+ "send <msg> invia un msg sulla chat\n"
				+ "receive visualizza i msg ricevuti sulla chat\n"
				);

	}


}
