
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
    private static String FILE_DIR;
    private static String METHOD_KEY = "METHOD";
    private static String TARGET_KEY = "TARGET";
    private static String VERSION_KEY = "VERSION";
    private static int HTTP_STATUS_OK = 200;
    private static int HTTP_STATUS_NOT_FOUND = 404;


    public static void main(String[] args) throws IOException {
        parseEnvs(args);
        createFileDirectory(FILE_DIR);
        createIntialFile(FILE_DIR, "foo");
        System.out.printf("use %s as file directory%n", FILE_DIR);

        try (ServerSocket serverSocket = new ServerSocket(PORT_NUMBER)) {
            serverSocket.setReuseAddress(true);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                processRequest(clientSocket);
            }
        }
    }

    private static void createFileDirectory(String fileDir) throws IOException {
        File dir = new File(fileDir);
        if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Failed to create directories: " + fileDir);
            }
    }

    private static void createIntialFile(String fileDir, String fileName) throws IOException {
        File file = new File(String.format("%s%s",fileDir, fileName));
        if (file.exists()) {
            file.delete();
        }
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write("Hello, World!");
        }
    }

    private static void parseEnvs(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("No file directory provided");
        } else {
            FILE_DIR = args[0];
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
                } else if (requestPath.startsWith("/files/")) {
                    processFileRequest(requestData);
                }
                else {
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

        private void processFileRequest(List<String> requestData) throws IOException {
            String target = parseRequestLine(requestData).get(TARGET_KEY);
            String[] split = target.split("/");
            String fileName = split[2];
            String content;

            if(fileName.equals("foo")) {
                File file = new File(String.format("%s%s",FILE_DIR, fileName));
                try(BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
                    String inputLine;
                    List<String> inputLines = new ArrayList<>();
                    while ((inputLine = bufferedReader.readLine()) != null) {
                        inputLines.add(inputLine);
                    }
                    content = String.join("", inputLines);
                }
                sendResponse(clientSocket, HTTP_STATUS_OK, Optional.of(content) );
            }
            else {
                sendResponse(clientSocket, HTTP_STATUS_NOT_FOUND, Optional.empty() );
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
    }
}
