package com.saeid;

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
    private static final String METHOD_KEY = "METHOD";
    private static final String TARGET_KEY = "TARGET";
    private static final String VERSION_KEY = "VERSION";
    private static final String REQUEST_LINE = "requestLine";
    private static final String REQUEST_HEADERS = "headers";
    private static final String REQUEST_BODY = "body";
    private static final String CONTENT_LENGHT_HEADER = "Content-Length: ";


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
                Map<String, String> requestData = readRequestData(clientSocket);
                Map<String, String> requestLineElements = parseRequestLine(requestData.get(REQUEST_LINE));
                Map<String, String> requestHeaders = parseHeaders(requestData.get(REQUEST_HEADERS));
                String requestPath = requestLineElements.get(TARGET_KEY);
                if (requestPath.equals("/")) {
                    sendResponse(clientSocket, TEXT_CONTENT_TYPE, HttpStatus.OK, Optional.empty());
                } else if (requestPath.startsWith("/echo/")) {
                    String[] pathElements = requestPath.split("/");
                    sendResponse(clientSocket, TEXT_CONTENT_TYPE, HttpStatus.OK, Optional.of(pathElements[2]));
                } else if (requestPath.startsWith("/user-agent")) {
                    sendResponse(clientSocket, TEXT_CONTENT_TYPE, HttpStatus.OK, Optional.of(requestHeaders.get(USER_AGENT)));
                } else if (requestPath.startsWith("/files/")) {
                    processFileRequest(requestData, args);
                } else {
                    sendResponse(clientSocket, TEXT_CONTENT_TYPE, HttpStatus.NOT_FOUND, Optional.empty());
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

        private void processFileRequest(Map<String, String> requestData, String[] args) throws IOException {
            String method = parseRequestLine(requestData.get(REQUEST_LINE)).get(METHOD_KEY);
            if (method.equals("GET")) {
                processFileReadRequest(requestData, args);
            } else if (method.equals("POST")) {
                processFileWriteRequest(requestData, args);
            }
        }

        private Map<String, String> parseHeaders(String requestHeaders) {
            Map<String, String> requestHeardMap = new HashMap<>();
            Arrays.stream(requestHeaders.split(",")).forEach(header -> {
                String[] split = header.split(":");
                requestHeardMap.put(split[0].trim(), split[1].trim());
            });
            return requestHeardMap;
        }

        private void processFileReadRequest(Map<String, String> requestData, String[] args) throws IOException {
            parseEnvs(args);
            String target = parseRequestLine(requestData.get(REQUEST_LINE)).get(TARGET_KEY);
            String[] split = target.split("/");
            String fileName = split[2];
            File file = new File(String.format("%s%s", FILE_DIR, fileName));
            String content;

            if (file.exists()) {
                try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
                    String inputLine;
                    List<String> inputLines = new ArrayList<>();
                    while ((inputLine = bufferedReader.readLine()) != null) {
                        inputLines.add(inputLine);
                    }
                    content = String.join("", inputLines);
                }
                sendResponse(clientSocket, BINARY_CONTENT_TYPE, HttpStatus.OK, Optional.of(content));
            } else {
                sendResponse(clientSocket, BINARY_CONTENT_TYPE, HttpStatus.NOT_FOUND, Optional.empty());
            }
        }

        private void processFileWriteRequest(Map<String, String> requestData, String[] args) throws IOException {
            parseEnvs(args);
            String content = requestData.get(REQUEST_BODY);
            String target = parseRequestLine(requestData.get(REQUEST_LINE)).get(TARGET_KEY);
            String[] split = target.split("/");
            String fileName = split[2];

            File dir = new File(FILE_DIR);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    throw new IllegalArgumentException("Can't create dir");
                }
            }
            File file = new File(dir, fileName);

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(content);
                writer.flush();
            }

            sendResponse(clientSocket, BINARY_CONTENT_TYPE, HttpStatus.CREATED, Optional.of(content));
        }

    }

    private static Map<String, String> parseRequestLine(String requestLine) {
        Map<String, String> requestLineMap = new HashMap<>();
        List<String> requestLineElements = Arrays.stream(requestLine.split(" ")).toList();
        requestLineMap.put(METHOD_KEY, requestLineElements.get(0));
        requestLineMap.put(TARGET_KEY, requestLineElements.get(1));
        requestLineMap.put(VERSION_KEY, requestLineElements.get(2));
        return requestLineMap;
    }

    private static Map<String, String> readRequestData(Socket clientSocket) throws IOException {
        int contentLength = 0;
        List<String> requestData = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        String inputLine;
        while ((inputLine = reader.readLine()) != null && !inputLine.isEmpty()) {
            requestData.add(inputLine);
            if (inputLine.startsWith(CONTENT_LENGHT_HEADER)) {
                contentLength = Integer.parseInt(inputLine.substring(CONTENT_LENGHT_HEADER.length()));
            }
        }
        if (contentLength > 0) {
            char[] content = new char[contentLength];
            int read = reader.read(content, 0, contentLength);
            requestData.add(new String(content, 0, read));
        }
        return parserqguestdata(requestData, contentLength);
    }

    private static Map<String, String> parserqguestdata(List<String> requestData, int contentLength) {
        Map<String, String> requestDataMap = new HashMap<>();
        requestDataMap.put(REQUEST_LINE, requestData.getFirst());
        if (contentLength > 0) {
            requestDataMap.put(REQUEST_BODY, requestData.getLast());
            addHeaders(requestData, requestDataMap, true);
        } else {
            addHeaders(requestData, requestDataMap, false);
        }
        return requestDataMap;
    }

    private static void addHeaders(List<String> requestData, Map<String, String> requestDataMap, boolean hasBody) {
        int endIndex;
        if (hasBody) {
            endIndex = requestData.size() - 1;
        } else {
            endIndex = requestData.size();
        }

        StringBuilder headers = new StringBuilder();
        for (int i = 1; i < endIndex; i++) {
            String header = requestData.get(i).trim();
            headers.append(header);
            if (i < endIndex - 1) {
                headers.append(",");
            }
        }
        requestDataMap.put(REQUEST_HEADERS, headers.toString());
    }

    private static void sendResponse(Socket clientSocket, String contentType, HttpStatus httpStatus, Optional<String> body) throws IOException {
        OutputStream outputStream = clientSocket.getOutputStream();
        PrintWriter writer = new PrintWriter(outputStream);
        writer.write(String.format("%s %s%s", HTTP_VERSION, httpStatus.toString(), CRLF));
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

    private static void parseEnvs(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("No file directory provided");
        } else {
            FILE_DIR = args[1];
        }
    }
}

