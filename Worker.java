import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;

public class Worker implements Runnable {

    private SocketChannel sc;
    private ByteBuffer messinput;
    private ByteBuffer messoutput;
    private boolean check;
    private String nomeutente;


    public Worker(SocketChannel socketchannel){
        this.sc = socketchannel;
        this.messinput = ByteBuffer.allocate(1024);
        this.messoutput = ByteBuffer.allocate(1024);
        this.check = true;
    }


    // usato da InvitiOnlineServer; si occupa di estrarre dalla lista di un utente (se online) gli inviti fatti
    // ai vari documenti mentre è online e li invia a invitionlineclient

    @Override
    public void run() {
        boolean verificaOnline;
        StringBuilder nomeutenteaux = new StringBuilder();
        Utente u;
        List<String> listainvitionline;
        try {

                messinput.clear();

                int numeroletti = sc.read(messinput);
                boolean checkworker = false;
                int numerodimensione = messinput.flip().getInt() + 4;
                while(!checkworker){

                    while(messinput.hasRemaining()){
                        nomeutenteaux.append((char)messinput.get());
                        //System.out.println("position: " + messinput.position());
                        //System.out.println("limit: " + messinput.limit());
                    }
                    if(numeroletti == numerodimensione){
                        checkworker = true;
                    }
                    else{
                        messinput.clear();
                        numeroletti = numeroletti + sc.read(messinput);
                    }
                }

                    nomeutente = nomeutenteaux.toString();

                    u = ServerRichieste.getUtenteByName(nomeutente);

                    while(check){
                        verificaOnline = u.IsOnline();
                        if (verificaOnline == true) {

                            listainvitionline = u.ListaInvitiOnline();
                            int elementi = listainvitionline.size();

                            if (elementi > 0) {
                                messoutput.putInt(0);
                                int lunghezza = 0;
                                for (String elem : listainvitionline) {
                                    lunghezza = lunghezza + elem.getBytes().length;
                                    messoutput.put(elem.getBytes());

                                }
                                int posizionebuffer = messoutput.position();
                                messoutput.position(0);
                                messoutput.putInt(lunghezza);
                                messoutput.position(posizionebuffer);
                                messoutput.flip();
                                while (messoutput.hasRemaining()) {
                                    sc.write(messoutput);
                                }
                                u.CancellaInvitiOnline();
                            }
                    }
                    else {
                        System.out.println("[Worker] l'utente non è online");
                        check = false;
                    }
                }
            } catch (IOException ex) {
           System.out.println("il listener degli inviti online (lato server) ha smesso di funzionare");
        }
    }
}
