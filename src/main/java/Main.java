
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Main {
    private static final String USER_AGENT = "User-Agent";
    public static void main(String[] args) throws IOException {
        System.out.println("Logs from your program will appear here!");
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(4221);
            serverSocket.setReuseAddress(true);
            Socket clientSocket = serverSocket.accept();
            System.out.println("accepted new connection");

            List<String> requestData = readRequestData(clientSocket);
            String requestPath = readReadPath(requestData);
            if(requestPath.equals("/")) {
                sendResponse(clientSocket, 200,Optional.empty());
            }
            if (requestPath.startsWith("/echo/")) {
                String[] pathElements = requestPath.split("/");
                sendResponse(clientSocket, 200,Optional.of(pathElements[2]));
            } if (requestPath.startsWith("/user-agent/")) {
                Map<String, String> headers = readHeaders(requestData);
                sendResponse(clientSocket, 200, Optional.of(headers.get(USER_AGENT)));
            }
            else {
                sendResponse(clientSocket, 404, Optional.empty());
            }
        } catch (IOException e) {
            serverSocket.close();
            System.out.println("IOException: " + e.getMessage());

        }
    }

    private static String readReadPath(List<String> requestData) throws IOException {
        List<String> requestLine = Arrays.stream(requestData.getFirst().split(" ")).toList();
        return requestLine.get(1);
    }

    private static Map<String, String> readHeaders(List<String> requestData) throws IOException {
        Map<String, String> headers = new HashMap<>();
        for(int i=1 ; i < requestData.size() ; i++) {
            String header = requestData.get(i);
            String[] split = header.split(":");
            headers.put(split[0], split[1]);
        }
        return headers;
    }

    private static List<String> readRequestData(Socket clientSocket) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        String inputLine;
        List<String> requestLines = new ArrayList<>();
        while (!(inputLine = reader.readLine()).isEmpty())
            requestLines.add(inputLine);
        return requestLines;
    }

    private static void sendResponse(Socket clientSocket, int statusCode, Optional<String> body) throws IOException {
        String status = buildStatusString(statusCode);
        OutputStream outputStream = clientSocket.getOutputStream();
        PrintWriter writer = new PrintWriter(outputStream);
        writer.write("HTTP/1.1 " + status + "\r\n");
        if(body.isPresent()) {
            String bodyContent = body.get();
            int length = bodyContent.getBytes().length;
            writer.write("Content-Length: "+length+"\r\n");
            writer.write("Content-Type: text/plain\r\n");
            writer.write("\r\n");
            writer.write(body.get());
            writer.write("\r\n");
        }
        writer.write("\r\n");
        writer.flush();
    }

    private static String buildStatusString(int statusCode) {
        if (statusCode == 200) {
            return "200 OK";
        } else if (statusCode == 404) {
            return "404 Not Found";
        }
        return "";
    }
}
