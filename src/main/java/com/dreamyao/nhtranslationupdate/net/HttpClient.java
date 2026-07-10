package com.dreamyao.nhtranslationupdate.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.dreamyao.nhtranslationupdate.config.UpdateConfig;

public final class HttpClient {

    private static final int MAX_REDIRECTS = 5;
    private final UpdateConfig config;

    public HttpClient(UpdateConfig config) {
        this.config = config;
    }

    public String getUtf8(String url, int maximumBytes) throws IOException {
        HttpURLConnection connection = open(url);
        try (InputStream input = connection.getInputStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copyBounded(input, output, maximumBytes);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    public void download(String url, Path destination, long expectedSize) throws IOException {
        if (expectedSize <= 0 || expectedSize > config.maxDownloadBytes) {
            throw new IOException("Artifact size is outside configured limits: " + expectedSize);
        }
        Files.createDirectories(
            destination.toAbsolutePath()
                .normalize()
                .getParent());
        Files.deleteIfExists(destination);
        HttpURLConnection connection = open(url);
        try {
            long contentLength = connection.getContentLengthLong();
            if (contentLength > config.maxDownloadBytes || (contentLength >= 0 && contentLength != expectedSize)) {
                throw new IOException("Unexpected Content-Length: " + contentLength);
            }
            try (InputStream input = connection.getInputStream();
                OutputStream output = Files.newOutputStream(destination)) {
                long downloaded = copyBounded(input, output, config.maxDownloadBytes);
                if (downloaded != expectedSize) {
                    throw new IOException("Unexpected artifact size: expected " + expectedSize + ", got " + downloaded);
                }
            }
        } catch (IOException exception) {
            Files.deleteIfExists(destination);
            throw exception;
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String value) throws IOException {
        URL current = new URL(value);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            requireAllowedProtocol(current);
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(config.connectTimeoutMillis);
            connection.setReadTimeout(config.readTimeoutMillis);
            connection.setRequestProperty("User-Agent", "NHTranslationUpdate/1.7.10");
            connection.setRequestProperty("Accept", "application/json, application/zip, */*;q=0.5");
            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) return connection;
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IOException("Redirect without Location header");
                current = new URL(current, location);
                continue;
            }
            connection.disconnect();
            throw new IOException("HTTP " + status + " for " + current);
        }
        throw new IOException("Too many redirects for " + value);
    }

    private void requireAllowedProtocol(URL url) throws IOException {
        String protocol = url.getProtocol();
        if (!"https".equalsIgnoreCase(protocol) && !(config.allowHttp && "http".equalsIgnoreCase(protocol))) {
            throw new IOException("Only HTTPS downloads are allowed: " + url);
        }
    }

    private static long copyBounded(InputStream input, OutputStream output, long maximumBytes) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            total += read;
            if (total > maximumBytes) throw new IOException("Download exceeded size limit");
            output.write(buffer, 0, read);
        }
        return total;
    }
}
