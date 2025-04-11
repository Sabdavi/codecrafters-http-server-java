import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
    public static void main(String[] args) {
        System.out.println("Logs from your program will appear here!");
        try (ServerSocket serverSocket = new ServerSocket(4221)) {
            serverSocket.setReuseAddress(true);
            Socket clientSocket = serverSocket.accept();
            System.out.println("accepted new connection");
            try (OutputStream outputStream = clientSocket.getOutputStream()) {
                PrintWriter writer = new PrintWriter(outputStream, true);
                writer.print("HTTP/1.1 200 OK\r\n");
                writer.print("Content-Length: 0\r\n");
                writer.print("\r\n");
                writer.flush();
            }
            clientSocket.close();
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());

        }
    }
}
