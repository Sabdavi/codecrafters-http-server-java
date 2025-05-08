
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static final String USER_AGENT = "User-Agent";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_LENGTH = "Content-Length";
    private static final String HTTP_VERSION = "HTTP/1.1";
    private static final String CRLF = "\r\n";
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final int PORT_NUMBER = 4221;


    public static void main(String[] args) throws IOException {
        System.out.println("Logs from your program will appear here!");
        try (ServerSocket serverSocket = new ServerSocket(PORT_NUMBER)) {
            serverSocket.setReuseAddress(true);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                processRequest(clientSocket);
            }
        }
    }

    private static void processRequest(Socket clientSocket) {
        executorService.execute(new RequestProcessingTask(clientSocket));
    }

    private static class RequestProcessingTask implements Runnable {

        private final Socket clientSocket;

        public RequestProcessingTask(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            System.out.println("executing request in thread : " + Thread.currentThread().getName());
            try {
                List<String> requestData = readRequestData(clientSocket);
                Map<String, String> requestLineElements = parseRequestLine(requestData);
                String requestPath = requestLineElements.get("TARGET");
                if (requestPath.equals("/")) {
                    sendResponse(clientSocket, 200, Optional.empty());
                } else if (requestPath.startsWith("/echo/")) {
                    String[] pathElements = requestPath.split("/");
                    sendResponse(clientSocket, 200, Optional.of(pathElements[2]));
                } else if (requestPath.startsWith("/user-agent")) {
                    Map<String, String> headers = readHeaders(requestData);
                    sendResponse(clientSocket, 200, Optional.of(headers.get(USER_AGENT)));
                } else {
                    sendResponse(clientSocket, 404, Optional.empty());
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static Map<String, String> parseRequestLine(List<String> requestData) throws IOException {
        Map<String, String> requestLine = new HashMap<>();
        List<String> requestLineElements = Arrays.stream(requestData.getFirst().split(" ")).toList();
        requestLine.put("METHOD", requestLineElements.get(0));
        requestLine.put("TARGET", requestLineElements.get(1));
        requestLine.put("VERSION", requestLineElements.get(2));
        return requestLine;
    }

    private static Map<String, String> readHeaders(List<String> requestData) throws IOException {
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < requestData.size(); i++) {
            String header = requestData.get(i);
            String[] split = header.split(":");
            headers.put(split[0].trim(), split[1].trim());
        }
        return headers;
    }

    private static List<String> readRequestData(Socket clientSocket) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        String inputLine;
        List<String> requestData = new ArrayList<>();
        while (!(inputLine = reader.readLine()).isEmpty())
            requestData.add(inputLine);
        return requestData;
    }

    private static void sendResponse(Socket clientSocket, int statusCode, Optional<String> body) throws IOException {
        String status = buildStatusString(statusCode);
        OutputStream outputStream = clientSocket.getOutputStream();
        PrintWriter writer = new PrintWriter(outputStream);
        writer.write(String.format("%s %s %s", HTTP_VERSION, status, CRLF));
        if (body.isPresent()) {
            String bodyContent = body.get();
            int length = bodyContent.getBytes().length;
            writer.write(String.format("%s : %d %s", CONTENT_LENGTH, length, CRLF));
            writer.write(String.format("%s : text/plain %s", CONTENT_TYPE, CRLF));
            writer.write(CRLF);
            writer.write(body.get());
            writer.write(CRLF);
        }
        writer.write(CRLF);
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

    private void test() {

    }
}
