/**
 *
 *  @author Lichota Krzysztof S12457
 *
 */

package zad1;


import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

public class Client extends JFrame {

    private SocketChannel socketChannel;
    private String userName;

    private JTextPane chat;
    private JList<String> users;
    private JTextField messageBox;
    private JButton send;

    public static void main(String[] args) {
        final String name = JOptionPane.showInputDialog(null, "Podaj nick: ", "Zaloguj się", JOptionPane.QUESTION_MESSAGE);
        if (name == null) return;

        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                JFrame chatFrame = null;
                try {
                    chatFrame = new Client("localhost", 8888, name);
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(chatFrame, "Problem z połączeniem.", "Błąd", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                chatFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                chatFrame.setTitle("Chat - " + name);
                chatFrame.setResizable(false);
                chatFrame.setVisible(true);
            }
        });
    }

    public Client(String ip, int port, String userName) throws IOException {
        this.userName = userName;

        connect(ip, port);
        login();
        initGUI();
        pack();

        new Thread(new ReadingThread(chat, users, socketChannel)).start();
    }

    private void connect(String ip, int port) throws IOException {
        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        socketChannel.connect(new InetSocketAddress(ip, port));
        while (!socketChannel.finishConnect()) ;
    }

    private void login() throws IOException {
        String loginCommand = "login|" + userName + "\n";
        send(loginCommand);
    }

    private void logout() throws IOException {
        String loginCommand = "logout|" + userName + "\n";
        send(loginCommand);
        socketChannel.close();
    }

    private void send(String data) throws IOException {
        ByteBuffer dataBuffer = ByteBuffer.wrap(data.getBytes());

        socketChannel.write(dataBuffer);
    }

    private void initGUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                try {
                    logout();
                } catch (IOException e1) {
                }
            }
        });

        chat = new JTextPane();
        chat.setPreferredSize(new Dimension(300, 300));
        chat.setEditable(false);
        ((DefaultCaret) chat.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        users = new JList<>();
        users.setPreferredSize(new Dimension(150, 300));
        messageBox = new JTextField(48);
        SendAction sendAction = new SendAction(socketChannel, messageBox, "Wyślij", this);
        send = new JButton(sendAction);

        JPanel sendPanel = new JPanel();
        sendPanel.add(messageBox);
        sendPanel.add(send);
        sendPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sendAction");
        sendPanel.getActionMap().put("sendAction", sendAction);

        add(sendPanel, BorderLayout.SOUTH);

        JPanel chatPanel = new JPanel();
        JScrollPane chatPane = new JScrollPane(chat);
        chatPane.setHorizontalScrollBar(null);
        chatPane.setPreferredSize(chat.getPreferredSize());
        chatPanel.add(chatPane);
        chatPane = new JScrollPane(users);
        chatPane.setPreferredSize(users.getPreferredSize());
        chatPane.setHorizontalScrollBar(null);
        chatPanel.add(chatPane);

        add(chatPanel, BorderLayout.CENTER);
    }
}

class SendAction extends AbstractAction {

    private SocketChannel socketChannel;
    private JTextField textField;
    private JFrame frame;

    public SendAction(SocketChannel socketChannel, JTextField textField, String desc, JFrame frame) {
        this.socketChannel = socketChannel;
        this.textField = textField;
        this.frame = frame;
        putValue(NAME, desc);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String newMsg = textField.getText();
        if (newMsg == null || newMsg.equals("")) return;
        newMsg = "msg|" + newMsg.replace('\n', ' ').replace('|', ' ') + '\n';

        ByteBuffer msgBuffer = ByteBuffer.wrap(newMsg.getBytes());

        try {
            socketChannel.write(msgBuffer);
        } catch (IOException e1) {
            JOptionPane.showMessageDialog(frame, "Nie udało się wysłać wiadomości!", "Błąd", JOptionPane.ERROR_MESSAGE);
        }

        textField.setText("");
    }
}

class ReadingThread implements Runnable {

    private JTextPane chat;
    private JList<String> usersList;
    private SocketChannel socketChannel;
    private Style nickStyle, msgStyle;

    private ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Server.BUFFER_SIZE);
    private static Charset charset = Charset.forName("UTF-8");

    public ReadingThread(JTextPane chat, JList<String> usersList, SocketChannel socketChannel) {
        this.chat = chat;
        this.usersList = usersList;
        this.socketChannel = socketChannel;

        StyledDocument styledDocument = chat.getStyledDocument();
        nickStyle = styledDocument.addStyle("nickStyle", null);
        StyleConstants.setForeground(nickStyle, Color.BLUE);
        StyleConstants.setBold(nickStyle, true);
    }

    @Override
    public void run() {
        while (socketChannel.isOpen()) {
            try {
                String data = read();
                if (data == null) continue;
                service(data);
            } catch (IOException e) {
            }
        }
    }

    private String read() throws IOException {
        byteBuffer.clear();
        int readBytes = socketChannel.read(byteBuffer);
        if (readBytes <= 0) return null;

        StringBuffer stringBuffer = new StringBuffer();
        while (readBytes > 0) {
            byteBuffer.flip();
            CharBuffer charBuffer = charset.decode(byteBuffer);

            while (charBuffer.hasRemaining()) {
                char c = charBuffer.get();
                if (c == '\n') return stringBuffer.toString();
                else stringBuffer.append(c);
            }

            byteBuffer.clear();
            readBytes = socketChannel.read(byteBuffer);
        }

        return null;
    }

    private void service(String data) {
        String[] splittedData = Server.splitRequest(data);
        if (splittedData[0].equals("msg")) putMessage(splittedData[1]);
        else if (splittedData[0].equals("users")) putListOfUsers(splittedData[1].split("\\|"));
    }

    private void putMessage(String msg) {
        int msgStartInd = msg.indexOf(':');
        msgStartInd = msg.indexOf(':', msgStartInd + 1);

        String nick = msg.substring(0, msgStartInd);
        Document doc = chat.getDocument();

        try {
            doc.insertString(doc.getLength(), nick, nickStyle);
            doc.insertString(doc.getLength(), msg.substring(msgStartInd, msg.length()) + '\n', StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE));
        } catch (BadLocationException e) {}
    }

    private void putListOfUsers(String[] users) {
        usersList.setListData(users);
    }
}