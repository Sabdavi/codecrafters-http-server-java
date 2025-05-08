
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
    private static final String TEXT_CONTENT_TYPE = "text/plain";
    private static final String BINARY_CONTENT_TYPE = "application/octet-stream";
    private static final String HTTP_VERSION = "HTTP/1.1";
    private static final String CRLF = "\r\n";
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final int PORT_NUMBER = 4221;
    private static String FILE_DIR;
    private static String METHOD_KEY = "METHOD";
    private static String TARGET_KEY = "TARGET";
    private static String VERSION_KEY = "VERSION";
    private static int HTTP_STATUS_OK = 200;
    private static int HTTP_STATUS_NOT_FOUND = 404;


    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT_NUMBER)) {
            serverSocket.setReuseAddress(true);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                processRequest(clientSocket, args);
            }
        }
    }


    private static void processRequest(Socket clientSocket, String[] args) {
        executorService.execute(new RequestProcessingTask(clientSocket, args));
    }

    private static class RequestProcessingTask implements Runnable {

        private final Socket clientSocket;
        private final String[] args;

        public RequestProcessingTask(Socket clientSocket, String[] args) {
            this.clientSocket = clientSocket;
            this.args = args;
        }

        @Override
        public void run() {
            System.out.println("executing request in thread : " + Thread.currentThread().getName());
            try {
                List<String> requestData = readRequestData(clientSocket);
                Map<String, String> requestLineElements = parseRequestLine(requestData);
                String requestPath = requestLineElements.get("TARGET");
                if (requestPath.equals("/")) {
                    sendResponse(clientSocket, TEXT_CONTENT_TYPE, 200, Optional.empty());
                } else if (requestPath.startsWith("/echo/")) {
                    String[] pathElements = requestPath.split("/");
                    sendResponse(clientSocket, TEXT_CONTENT_TYPE, 200, Optional.of(pathElements[2]));
                } else if (requestPath.startsWith("/user-agent")) {
                    Map<String, String> headers = readHeaders(requestData);
                    sendResponse(clientSocket, TEXT_CONTENT_TYPE, 200, Optional.of(headers.get(USER_AGENT)));
                } else if (requestPath.startsWith("/files/")) {
                    processFileRequest(requestData, args);
                }
                else {
                    sendResponse(clientSocket, TEXT_CONTENT_TYPE, 404, Optional.empty());
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

        private void processFileRequest(List<String> requestData, String[] args) throws IOException {
            parseEnvs(args);
            String target = parseRequestLine(requestData).get(TARGET_KEY);
            String[] split = target.split("/");
            String fileName = split[2];
            File file = new File(String.format("%s%s",FILE_DIR, fileName));
            String content;

            if(file.exists()) {
                try(BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
                    String inputLine;
                    List<String> inputLines = new ArrayList<>();
                    while ((inputLine = bufferedReader.readLine()) != null) {
                        inputLines.add(inputLine);
                    }
                    content = String.join("", inputLines);
                }
                sendResponse(clientSocket, BINARY_CONTENT_TYPE, HTTP_STATUS_OK, Optional.of(content) );
            }
            else {
                sendResponse(clientSocket, BINARY_CONTENT_TYPE, HTTP_STATUS_NOT_FOUND, Optional.empty() );
            }
        }

        private static Map<String, String> parseRequestLine(List<String> requestData) {
            Map<String, String> requestLine = new HashMap<>();
            List<String> requestLineElements = Arrays.stream(requestData.getFirst().split(" ")).toList();
            requestLine.put(METHOD_KEY, requestLineElements.get(0));
            requestLine.put(TARGET_KEY, requestLineElements.get(1));
            requestLine.put(VERSION_KEY, requestLineElements.get(2));
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

        private static void sendResponse(Socket clientSocket, String contentType, int statusCode, Optional<String> body) throws IOException {
            String status = buildStatusString(statusCode);
            OutputStream outputStream = clientSocket.getOutputStream();
            PrintWriter writer = new PrintWriter(outputStream);
            writer.write(String.format("%s %s%s", HTTP_VERSION, status, CRLF));
            if (body.isPresent()) {
                String bodyContent = body.get();
                int length = bodyContent.getBytes().length;
                writer.write(String.format("%s:%d%s", CONTENT_LENGTH, length, CRLF));
                writer.write(String.format("%s:%s%s", CONTENT_TYPE, contentType, CRLF));
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

        private static void parseEnvs(String[] args) {
            if (args.length == 0) {
                throw new IllegalArgumentException("No file directory provided");
            } else {
                FILE_DIR = args[1];
            }
        }
    }
}
