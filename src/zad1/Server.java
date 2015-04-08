/**
 *
 *  @author Lichota Krzysztof S12457
 *
 */

package zad1;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Logger;

public class Server {

    public static void main(String[] args) {
        try {
            new Server("localhost", 8888);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Nie można utworzyć serwera!");
            System.exit(1);
        }
    }

    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private boolean serverIsRunning;

    private Logger logger = Logger.getLogger(this.toString());

    private static Charset charset = Charset.forName("ISO-8859-2");
    public static final int BUFFER_SIZE = 2048;
    public static final char SEPARATOR = '|';
    private ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

    private Map<SocketChannel, String> usersMap = new LinkedHashMap<>();


    public Server(String ip, int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(ip, port));

        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        serverIsRunning = true;

        logger.info("Server started. " + serverSocketChannel.getLocalAddress());

        serviceConnections();
    }

    private void serviceConnections() throws IOException {
        while (isServerRunning()) {
            selector.select();
            serviceUsers(selector.selectedKeys());
        }

        dispose();
    }

    private void serviceUsers(Set<SelectionKey> keys) {
        Iterator<SelectionKey> iter = keys.iterator();

        while (iter.hasNext()) {
            SelectionKey sKey = iter.next();
            iter.remove();

            serviceKey(sKey);
        }
    }

    private void serviceKey(SelectionKey sKey) {
        if (sKey.isAcceptable()) acceptKey();
        if (sKey.isReadable()) readKey(sKey);
    }

    private void acceptKey() {
        try {
            SocketChannel socketChannel = serverSocketChannel.accept();
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ);

            logger.info("New client connected. " + socketChannel.getRemoteAddress());
        } catch (IOException e) {
            logger.info(e.getMessage());
        }
    }

    private void readKey(SelectionKey sKey) {
        SocketChannel socketChannel = (SocketChannel) sKey.channel();
        if (!socketChannel.isOpen()) return;

        try {
            String request = readRequestFromSocket(socketChannel);
            if(request != null) serviceRequest(request, socketChannel);
        } catch (IOException e) {
            try {
                logoutUser(socketChannel);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private void serviceRequest(String request, SocketChannel socketChannel) throws IOException {
        String[] splittedRequest = splitRequest(request);
        logger.info("New '" + splittedRequest[0] + "' request from: " + socketChannel.getRemoteAddress());

        executeRequest(splittedRequest[0], splittedRequest[1], socketChannel);
    }

    private void executeRequest(String command, String arg, SocketChannel socketChannel) throws IOException {
        if(command.equals("login")) loginUser(arg, socketChannel);
        else if(command.equals("msg")) sendMessage(arg, socketChannel);
        else if(command.equals("logout")) logoutUser(socketChannel);
    }

    private void logoutUser(SocketChannel socketChannel) throws IOException {
        String name = usersMap.get(socketChannel);
        usersMap.remove(socketChannel);
        logger.info("User: " + name + socketChannel.getRemoteAddress() + " logout.");
        socketChannel.close();
        sendUsersList();
    }

    private void sendMessage(String msg, SocketChannel socketChannel) throws IOException {
        String name = usersMap.get(socketChannel);
        String message = "msg|" + name + socketChannel.getRemoteAddress() + ": " + msg + '\n';

        sendToAllUsers(message);
        logger.info("User " + name + socketChannel.getRemoteAddress() + " sent message: " + msg);
    }

    private void loginUser(String name, SocketChannel socketChannel) throws IOException {
        usersMap.put(socketChannel, name);
        logger.info("User from " + socketChannel.getRemoteAddress() + " logged as: " + name);
        sendUsersList();
    }

    private void sendUsersList() throws IOException {
        StringBuilder users = new StringBuilder("users");

        for(String userName : usersMap.values()){
            users.append("|" + userName);
        }
        users.append('\n');

        sendToAllUsers(users.toString());
        logger.info("List of users was resent.");
    }

    private void sendToAllUsers(String data) throws IOException {
        ByteBuffer dataBuffer = ByteBuffer.wrap(data.getBytes());

        for(SelectionKey sKey : selector.keys()){
            if(sKey.isValid() && (sKey.channel() instanceof SocketChannel)) {
                SocketChannel channelToWrite = (SocketChannel) sKey.channel();
                channelToWrite.write(dataBuffer);
                dataBuffer.rewind();
            }
        }
    }

    public static String[] splitRequest(String request){
        String[] splittedRequest = new String[2];
        int startIndex, endIndex;

        endIndex = request.indexOf(SEPARATOR);
        splittedRequest[0] = request.substring(0, endIndex);
        startIndex = endIndex + 1;

        splittedRequest[1] = request.substring(startIndex, request.length());

        return splittedRequest;
    }

    private String readRequestFromSocket(SocketChannel socketChannel) throws IOException {
        StringBuffer stringBuffer = new StringBuffer();
        byteBuffer.clear();
        int readBytes = socketChannel.read(byteBuffer);

        while (readBytes > 0) {
            byteBuffer.flip();
            CharBuffer charBuffer = charset.decode(byteBuffer);

            while (charBuffer.hasRemaining()) {
                char c = charBuffer.get();
                if (c == '\n') return stringBuffer.toString();
                else stringBuffer.append(c);
            }

            readBytes = socketChannel.read(byteBuffer);
        }

        return null;
    }

    public synchronized void serverStop() {
        serverIsRunning = false;
        logger.info("Server stopped.");
    }

    public synchronized boolean isServerRunning() {
        return serverIsRunning;
    }

    private void dispose() throws IOException {
        selector.close();
        serverSocketChannel.close();
        logger.info("Server closed.");
    }

    public Logger getLogger() {
        return logger;
    }
}