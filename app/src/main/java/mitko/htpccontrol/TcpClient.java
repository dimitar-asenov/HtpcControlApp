package mitko.htpccontrol;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class TcpClient extends Thread {
    TcpClient(String serverIpAddress, int serverPort, Handler uiThreadHandler) {
        serverIpAddress_ = serverIpAddress;
        serverPort_ = serverPort;
        uiHandler_ = uiThreadHandler;
    }

    public void run() {

        reconnect();

        Looper.prepare();

        handler_ = new Handler() {
            public void handleMessage(Message msg) {
                handleMessageFromUI(msg);
            }
        };

        Looper.loop();
    }

    public void setServer(String serverIpAddress, int serverPort) {
        serverIpAddress_ = serverIpAddress;
        serverPort_ = serverPort;
        Message m = Message.obtain();
        m.obj = "reconnect";
        handler_.sendMessage(m);
    }

    public void send(String[] line) {
        Message m = Message.obtain();
        m.obj = encode(line);
        handler_.sendMessage(m);
    }

    public String lastError() { return lastErrorMessage_; }

    /////////////////////////////////////////////////////////////////////////////////

    private void handleMessageFromUI(Message msg) {
        String line = (String) msg.obj;

        if (line.equals("reconnect")) reconnect();
        else out_.println(line);
    }

    private String encode(String[] line) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (String word : line) {
            if (!first) result.append(' ');
            for (int i = 0; i < word.length(); ++i) {
                char c = word.charAt(i);
                switch (c) {
                    case '\n':
                        result.append("\\n");
                        break;
                    case '\r':
                        result.append("\\r");
                        break;
                    case ' ':
                        result.append("\\s");
                        break;
                    case '\\':
                        result.append("\\\\");
                        break;
                    default:
                        result.append(c);
                        break;
                }
            }
            first = false;
        }

        return result.toString();
    }

    private ArrayList<String> decode(String line) {
        ArrayList<String> result = new ArrayList<String>();

        for (String word :  line.split(" ")) {
            StringBuilder decodedWord = new StringBuilder();

            boolean isEscaped = false;
            for (int i = 0; i<word.length(); ++i) {
                char c = word.charAt(i);
                if (isEscaped)
                {
                    switch (c)
                    {
                        case 'n': decodedWord.append('\n'); break;
                        case 'r': decodedWord.append('\r'); break;
                        case 's': decodedWord.append(' '); break;
                        default: decodedWord.append(c); break;
                    }
                    isEscaped = false;
                }
                else {
                    if (c == '\\') isEscaped = true;
                    else decodedWord.append(c);
                }
            }

            result.add(decodedWord.toString());
        }

        return result;
    }

    private void sendToUi(ArrayList<ArrayList<String>> lines) {
            Message m = Message.obtain();
            m.obj = lines;
            uiHandler_.sendMessage(m);
    }

    private void reconnect() {
        boolean success = false;
        try {
            if (readerThread_ != null) {
                readerThread_ = null;
            }

            if (socket_ != null) {
                socket_.close();
                socket_ = null;
            }

            InetAddress serverAddress = InetAddress.getByName(serverIpAddress_);
            socket_ = new Socket(serverAddress, serverPort_);

            out_ = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket_.getOutputStream())), true);
            in_ = new BufferedReader(new InputStreamReader(socket_.getInputStream()));

            if (!in_.readLine().startsWith("TvControlServer")) {
                lastErrorMessage_ = "Connected to server, but incorrect welcome message";
                socket_.close();
                return;
            }

            success = true;
        }
        catch (UnknownHostException e) {
            lastErrorMessage_ = "Unknown host: " + e.getMessage();
        }
        catch (IOException e) {
            lastErrorMessage_ = "Socket error: " + e.getMessage();
        }
        finally {
            String toSend = success ? "Connection OK" : lastErrorMessage_;
            ArrayList<ArrayList<String>> lines = new ArrayList<ArrayList<String>>();
            lines.add(new ArrayList<String>());
            lines.get(0).add(toSend);
            sendToUi(lines);
        }

        if (success) {
            readerThread_ = new ReaderThread();
            readerThread_.start();
        }
    }

    class ReaderThread extends Thread {

        public void run() {
            try {
                while (true) {
                    ArrayList<ArrayList<String>> linesToSend = new ArrayList<ArrayList<String>>();

                    int remainingLines = 1;
                    while (remainingLines > 0) {
                        String rawLine = in_.readLine();
                        if (rawLine == null) return; // Connection closed

                        ArrayList<String> lineTokens = decode(rawLine);
                        remainingLines = Integer.parseInt(lineTokens.get(0));
                        lineTokens.remove(0);
                        linesToSend.add(lineTokens);
                    }

                    sendToUi(linesToSend);
                }
            } catch (IOException e) {
                // Do nothing and stop the thread.
            }
        }
    }

    private PrintWriter out_;
    private BufferedReader in_;
    private Socket socket_;
    private Thread readerThread_;

    private String serverIpAddress_;
    private int serverPort_;

    private String lastErrorMessage_;

    private Handler handler_;
    public Handler handler() { return handler_; }

    private Handler uiHandler_;
}
