
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;


public class InvitiOnlineClient extends Thread {

    private boolean check;
    private String nomeutente;
    private SocketChannel s;
    private ByteBuffer bufferinput;
    private ByteBuffer bufferoutput;


    public InvitiOnlineClient(SocketChannel socket, String nu){
        this.nomeutente = nu;
        this.s = socket;
        this.check = true;
        this.bufferinput = ByteBuffer.allocate(1024);
        this.bufferoutput = ByteBuffer.allocate(1024);
    }

    //  si occupa della gestione degli inviti online lato client
    public void run(){

        System.out.println("avvio il gestore di inviti online");
        String inv;

        try {
            // invio chi effettivamente sta aspettando un invito mentre è online a invitionlineserver
            bufferoutput.putInt(nomeutente.getBytes().length);
            bufferoutput.put(nomeutente.getBytes());
            bufferoutput.flip();
            while (bufferoutput.hasRemaining()) {
                s.write(bufferoutput);
            }
            bufferoutput.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
        while(check){
            try{
                // leggo la risposta inviata da invitionlineserver
                int numeroletti = s.read(bufferinput);
                if(numeroletti > 0 ) {
                    StringBuilder inviti = new StringBuilder();
                    boolean checkinviti = false;
                    int numerodimensione = bufferinput.flip().getInt() + 4;
                    while(!checkinviti){
                        while(bufferinput.hasRemaining()){
                            inviti.append((char)bufferinput.get());
                            System.out.println("position: " + bufferinput.position());
                            System.out.println("limit: " + bufferinput.limit());
                        }
                        if(numeroletti == numerodimensione){
                            checkinviti = true;
                        }
                        else{
                            bufferinput.clear();
                            numeroletti = numeroletti + s.read(bufferinput);
                        }
                    }

                    // ottengo il nome del documento a cui sono stato invitato e lo salvo in inv
                    inv = inviti.toString();
                    System.out.println("sono dopo inv");
                    if(!inv.isEmpty() && inv != null ){
                        System.out.println("Richiesta collaborazione per " + this.nomeutente + " al seguente documento " + inv);
                    }
                    bufferinput.clear();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            this.s.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void QuitInviti(){
        this.check = false;
    }
}
