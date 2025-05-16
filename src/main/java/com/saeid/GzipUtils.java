package com.saeid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

public class GzipUtils {

    public static byte[] compress(String input) throws IOException {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Can't compress empty string");
        }

        ByteArrayOutputStream byteArrayInputStream = new ByteArrayOutputStream();
        try(GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayInputStream)) {
            gzipOutputStream.write(input.getBytes(StandardCharsets.UTF_8));
        }
        return byteArrayInputStream.toByteArray();
    }
}
