import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;



public class InvitiOnlineServer extends Thread {

    private ServerSocketChannel s;
    private ThreadPoolExecutor th;
    private boolean check;


    public InvitiOnlineServer(){
        try {
            s = ServerSocketChannel.open();
            s.socket().bind(new InetSocketAddress("localhost",4343));
            th = new ThreadPoolExecutor(4,8,0, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
            check = true;
        } catch (IOException e) {
            System.out.println("il listener degli inviti online (lato server) ha smesso di funzionare");
        }

    }

    // utilizzato per ricevere gli inviti mentre l'utente è online (lato server)
    public void run() {

        SocketChannel sc;
        System.out.println("startato inivitionliserver");
        while(check){

            try {
                // mi metto in attesa di stabilire una nuova connessione con un utente quando è online (dopo che ha fatto la login)
                sc = s.accept();
                th.execute(new Worker(sc));
                th.setRejectedExecutionHandler(new RejectedExecutionHandler() {
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                        try {
                            executor.getQueue().put(r);
                        }
                        catch(InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (IOException e) {
                System.out.println("il listener degli inviti online (lato server) ha smesso di funzionare");
            }
        }
    }

    public void finisci(){
        this.check = false;
    }





}
