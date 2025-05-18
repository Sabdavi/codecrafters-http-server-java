package com.saeid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.saeid.HttpHeaders.*;
import static com.saeid.Main.*;

public class RequestProcessingTask implements Runnable {

    private final Socket clientSocket;
    private final String[] args;
    private final static Logger logger = LoggerFactory.getLogger(RequestProcessingTask.class);

    public RequestProcessingTask(Socket clientSocket, String[] args) {
        this.clientSocket = clientSocket;
        this.args = args;
    }

    @Override
    public void run() {
        logger.info("executing request in thread : {}", Thread.currentThread().getName());
        while (!clientSocket.isClosed()) {
            try {
                Map<String, String> requestData = readRequestData(clientSocket);
                if (requestData != null && !requestData.isEmpty()) {
                    Map<String, String> requestLineElements = parseRequestLine(requestData.get(REQUEST_LINE));
                    Map<String, String> requestHeaders = parseHeaders(requestData.get(REQUEST_HEADERS));
                    String body = requestData.get(REQUEST_BODY);
                    String requestPath = requestLineElements.get(TARGET_KEY);
                    if (requestPath.equals("/")) {
                        sendResponse(clientSocket, requestHeaders, CONTENT_ENCODING_DEFAULT, HttpStatus.OK, Optional.empty());
                    } else if (requestPath.startsWith("/echo/")) {
                        String[] pathElements = requestPath.split("/");
                        sendResponse(clientSocket, requestHeaders, DEFAULT_CONTENT_TYPE, HttpStatus.OK, Optional.of(pathElements[2]));
                    } else if (requestPath.startsWith("/user-agent")) {
                        sendResponse(clientSocket, requestHeaders, CONTENT_ENCODING_DEFAULT, HttpStatus.OK, Optional.of(requestHeaders.get(USER_AGENT)));
                    } else if (requestPath.startsWith("/files/")) {
                        processFileRequest(requestLineElements, requestHeaders, body, args);
                    } else {
                        sendResponse(clientSocket, requestHeaders, CONTENT_ENCODING_DEFAULT, HttpStatus.NOT_FOUND, Optional.empty());
                    }
                    closeConnectionIfNecessary(requestHeaders, clientSocket);
                }
            } catch (IOException e) {
                try {
                    clientSocket.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                e.printStackTrace();
            }
        }
    }

    private void closeConnectionIfNecessary(Map<String, String> requestHeaders, Socket clientSocket) {
        if (requestHeaders.containsKey(CONNECTION) && requestHeaders.get(CONNECTION).equals(CONNECTION_CLOSE)) {
            try {
                logger.info("Closing connection");
                clientSocket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String getContentEncoding(Map<String, String> requestHeaders) {

        if (requestHeaders.get(ACCEPT_ENCODING) == null) {
            return null;
        }
        List<String> requestedEncodings = Arrays.stream(requestHeaders.get(ACCEPT_ENCODING).split(",")).map(String::trim).toList();

        for (String supported : SUPPORTED_ENCODINGS) {
            if (requestedEncodings.contains(supported)) {
                return supported;
            }
        }
        return null;
    }

    private void processFileRequest(Map<String, String> requestLineElements, Map<String, String> requestHeaders, String body, String[] args) throws IOException {
        String method = requestLineElements.get(METHOD_KEY);
        String target = requestLineElements.get(TARGET_KEY);
        if (method.equals("GET")) {
            processFileReadRequest(target, requestHeaders, args);
        } else if (method.equals("POST")) {
            processFileWriteRequest(target, requestHeaders, body, args);
        }
    }

    private Map<String, String> parseHeaders(String requestHeaders) {
        Map<String, String> requestHeardMap = new HashMap<>();
        Arrays.stream(requestHeaders.split("\n")).forEach(header -> {
            String[] split = header.split(":");
            requestHeardMap.put(split[0].trim(), split[1].trim());
        });
        return requestHeardMap;
    }

    private void processFileReadRequest(String target, Map<String, String> requestHeaders, String[] args) throws IOException {
        parseEnvs(args);
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
            sendResponse(clientSocket, requestHeaders, BINARY_CONTENT_TYPE, HttpStatus.OK, Optional.of(content));
        } else {
            sendResponse(clientSocket, requestHeaders, BINARY_CONTENT_TYPE, HttpStatus.NOT_FOUND, Optional.empty());
        }
    }

    private void processFileWriteRequest(String target, Map<String, String> requestHeaders, String content, String[] args) throws IOException {
        parseEnvs(args);
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

        sendResponse(clientSocket, requestHeaders, BINARY_CONTENT_TYPE, HttpStatus.CREATED, Optional.of(content));
    }

    private Map<String, String> parseRequestLine(String requestLine) {
        Map<String, String> requestLineMap = new HashMap<>();
        List<String> requestLineElements = Arrays.stream(requestLine.split(" ")).toList();
        requestLineMap.put(METHOD_KEY, requestLineElements.get(0));
        requestLineMap.put(TARGET_KEY, requestLineElements.get(1));
        requestLineMap.put(VERSION_KEY, requestLineElements.get(2));
        return requestLineMap;
    }

    private Map<String, String> readRequestData(Socket clientSocket) throws IOException {
        int contentLength = 0;
        List<String> requestData = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        String inputLine;
        while ((inputLine = reader.readLine()) != null && !inputLine.isEmpty()) {
            requestData.add(inputLine);
            if (inputLine.startsWith(CONTENT_LENGTH)) {
                contentLength = Integer.parseInt(inputLine.substring(CONTENT_LENGTH.length()+2));
            }
        }
        if (contentLength > 0) {
            char[] content = new char[contentLength];
            int read = reader.read(content, 0, contentLength);
            requestData.add(new String(content, 0, read));
        }

        if (requestData.isEmpty()) {
            return Collections.emptyMap();
        }
        return parseRequestData(requestData, contentLength);
    }

    private Map<String, String> parseRequestData(List<String> requestData, int contentLength) {
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

    private void addHeaders(List<String> requestData, Map<String, String> requestDataMap, boolean hasBody) {
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
                headers.append("\n");
            }
        }
        requestDataMap.put(REQUEST_HEADERS, headers.toString());
    }

    private void sendResponse(Socket clientSocket, Map<String, String> requestHeaders, String contentType, HttpStatus httpStatus, Optional<String> body) throws IOException {
        OutputStream rawOutput = clientSocket.getOutputStream();
        BufferedOutputStream outputStream = new BufferedOutputStream(rawOutput);
        String contentEncoding = getContentEncoding(requestHeaders);

        outputStream.write(String.format("%s %s%s", HTTP_VERSION, httpStatus.toString(), CRLF).getBytes());
        if (requestHeaders.containsKey(CONNECTION) && requestHeaders.get(CONNECTION).equals(CONNECTION_CLOSE)) {
            outputStream.write(String.format("%s: %s%s", CONNECTION, CONNECTION_CLOSE, CRLF).getBytes());
        }

        if (body.isPresent()) {
            byte[] responseBody;
            boolean isGzip = contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip");

            if (isGzip) {
                responseBody = GzipUtils.compress(body.get());
            } else {
                responseBody = body.get().getBytes(StandardCharsets.UTF_8);
            }

            outputStream.write(String.format("%s: %d%s", CONTENT_LENGTH, responseBody.length, CRLF).getBytes());
            outputStream.write(String.format("%s: %s%s", CONTENT_TYPE, contentType, CRLF).getBytes());
            if (isGzip) {
                outputStream.write(String.format("%s: %s%s", CONTENT_ENCODING, contentEncoding, CRLF).getBytes());
            }
            outputStream.write(CRLF.getBytes());
            outputStream.flush();

            outputStream.write(responseBody);
            outputStream.flush();
        } else {
            outputStream.write(String.format("%s: 0%s", CONTENT_LENGTH, CRLF).getBytes());
            outputStream.write(String.format("%s: %s%s", CONTENT_TYPE, contentType, CRLF).getBytes());
            outputStream.write(CRLF.getBytes());
            outputStream.flush();
        }
    }

    private static void parseEnvs(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("No file directory provided");
        } else {
            FILE_DIR = args[1];
        }
    }
}
