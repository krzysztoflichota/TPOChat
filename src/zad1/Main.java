/**
 *
 *  @author Lichota Krzysztof S12457
 *
 */

package zad1;


import java.io.IOException;

public class Main {

    public static void main(final String[] args) throws IOException {
        new Thread(){
            @Override
            public void run() {
                    Server.main(args);
            }
        }.start();

        new Thread(){
            @Override
            public void run() {
                    Client.main(args);
            }
        }.start();

        new Thread(){
            @Override
            public void run() {
                    Client.main(args);
            }
        }.start();
    }
}
