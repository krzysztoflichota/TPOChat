/**
 *
 *  @author Lichota Krzysztof S12457
 *
 */

package zad1;



public class Main {

    public static void main(final String[] args) {
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
