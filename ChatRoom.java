import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingQueue;

public class ChatRoom extends Thread{

    private boolean check;
    private String nomeutente;
    private MulticastSocket socketChat;
    private InetAddress indirizzoGruppo;
    private int porta;
    private LinkedBlockingQueue<String> listadimessaggi; // ognuno ha la propria lista di messaggi dove prendono i messaggi
    // in multicast e li mettono in lista convertendoli in string.

    public ChatRoom(String nome, InetAddress ig, MulticastSocket sc, int porta){
        this.nomeutente = nome;
        this.socketChat = sc;
        this.indirizzoGruppo = ig;
        this.check = true;
        this.porta = porta;
        this.listadimessaggi = new LinkedBlockingQueue<>();
    }

    // metodo utilizzato nella receive per estrarre tutti i messaggi inviati fino a questo momento
    public synchronized LinkedBlockingQueue<String> EstraiMessaggiChat(){
        return listadimessaggi;
    }

    // metodo chiamato per disattivare la chat
    public void disattivaChat(){
        check = false;
    }

    // metodo che si occupa di ricevere i messaggi spediti lato client con la "send" e salvarli all'interno
    // di una coda per una futura "receive"
    public void run(){
        System.out.println("Chatroom iniziata");
        DatagramPacket pacchettoUDP;
        byte[] buffer = new byte[512];
        int timeout = 1500;

        try{
            String aux = "il seguente utente: " + this.nomeutente + " è online";
            byte[] auxbyte = aux.getBytes();
            DatagramPacket intromess = new DatagramPacket(auxbyte, auxbyte.length, indirizzoGruppo, porta);
            socketChat.send(intromess);
            socketChat.setSoTimeout(timeout);
            socketChat.joinGroup(indirizzoGruppo);

            while(check){
                pacchettoUDP = null;
                try{

                    pacchettoUDP = new DatagramPacket(buffer, buffer.length);
                    socketChat.receive(pacchettoUDP);

                    String messaggio = new String(pacchettoUDP.getData(), pacchettoUDP.getOffset(), pacchettoUDP.getLength());
                    listadimessaggi.add(messaggio);
                } catch (SocketTimeoutException e) {
                }
            }
            System.out.println("chat disabilitata");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
