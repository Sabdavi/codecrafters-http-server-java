package com.saeid;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    public static final String USER_AGENT = "User-Agent";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_ENCODING = "Content-Encoding";
    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    static final String CONTENT_ENCODING_DEFAULT = "text/plain";
    static final String DEFAULT_CONTENT_TYPE = "text/plain";
    public static final String BINARY_CONTENT_TYPE = "application/octet-stream";
    public static final String HTTP_VERSION = "HTTP/1.1";
    public static final String CRLF = "\r\n";
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final int PORT_NUMBER = 4221;
    public static String FILE_DIR;
    public static final String METHOD_KEY = "METHOD";
    public static final String TARGET_KEY = "TARGET";
    public static final String VERSION_KEY = "VERSION";
    public static final String REQUEST_LINE = "requestLine";
    public static final String REQUEST_HEADERS = "headers";
    public static final String REQUEST_BODY = "body";
    public static final String CONTENT_LENGTH_HEADER = "Content-Length: ";
    public static final String[] SUPPORTED_ENCODINGS = new String[]{"gzip"};


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
}

