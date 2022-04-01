import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class SezioneDocumento {

    private PrintWriter sezione;
    private String sodu; // sezione occupata dall'utente
    private String nomesezione;


    public SezioneDocumento(String nomefile, int i){
        String nomepercorso;
        String os = System.getProperty("os.name").toLowerCase();
        if(os.indexOf("win")>=0) nomepercorso = "\\" + nomefile + "\\"; //se siamo in windows
        else nomepercorso = nomefile + "/"; // se siamo in linux
        this.nomesezione = nomepercorso + "sezione-" + i + ".txt";
        System.out.println(nomesezione);
        try {
            this.sezione = new PrintWriter(this.nomesezione,"UTF-8");
        } catch (FileNotFoundException e) {
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    // metodo usato per capire se qualcuno sta occupando la sezione oppure no
    public synchronized String ottieniSodu(){
        return this.sodu;
    }

    // metodo usato per ottenere tutto il percorso della sezione fino ad arrivare alla sezione stessa
    public synchronized String getNomeSezione() {
        return this.nomesezione;
    }

    // metodo usato per aggiornare il contenuto di una sezione; prendo cioè il file aggiornato dal client e lo invio al server (end-edit)
    public synchronized void AggiornaSezione(SocketChannel client) throws FileNotFoundException {

        new PrintWriter(this.nomesezione).close();
        this.sezione = new PrintWriter(this.nomesezione);
        try {
            FileChannel auxFile = FileChannel.open(Paths.get(this.nomesezione), StandardOpenOption.WRITE);

                long transfered = 0;
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                try {

                    int letti;
                    while ((letti = client.read(buffer)) != -1) {
                        buffer.flip();
                        while (buffer.hasRemaining())
                            auxFile.write(buffer);
                        buffer.clear();
                    }

                    auxFile.close();
                    client.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void setSezione(PrintWriter sezione) {
        this.sezione = sezione;
    }

    // metodo usato per occupare una determinata sezione per un determinato utente
    public synchronized void OccupaSezPer(String u){
        if (this.sodu == null)
            this.sodu = u;
        else System.err.println("Sezione già occupata");
    }

    // metodo usato per liberare una determinata sezione per un determinato utente
    public synchronized void LiberaSez(){
        this.sodu = null;
    }
}
