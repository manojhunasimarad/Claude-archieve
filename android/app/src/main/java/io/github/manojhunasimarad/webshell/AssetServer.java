package io.github.manojhunasimarad.webshell;

import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A tiny web server that serves the app's bundled files (assets/www) on http://127.0.0.1:PORT.
 * A real http origin lets workers, WebAssembly, ES modules and service workers behave exactly as in Chrome.
 * It listens on the loopback address only, so nothing outside the phone can reach it.
 */
final class AssetServer implements Runnable {
    private static final String TAG = "AssetServer";
    private static final String ROOT = "www";
    private static AssetServer instance;

    private final AssetManager assets;
    private final int port;
    private final CountDownLatch ready = new CountDownLatch(1);
    private final ExecutorService pool = Executors.newFixedThreadPool(8);
    private volatile ServerSocket socket;
    private volatile IOException startError;

    private AssetServer(AssetManager assets, int port) {
        this.assets = assets;
        this.port = port;
    }

    /** Starts the server once per process and waits briefly until it is listening. */
    static synchronized boolean ensureStarted(AssetManager assets, int port) {
        if (instance != null && instance.socket != null && !instance.socket.isClosed()) return true;
        instance = new AssetServer(assets, port);
        Thread t = new Thread(instance, "asset-server");
        t.setDaemon(true);
        t.start();
        try {
            instance.ready.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        if (instance.startError != null) {
            Log.e(TAG, "Could not listen on port " + port, instance.startError);
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        try {
            ServerSocket s = new ServerSocket();
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 64);
            socket = s;
        } catch (IOException e) {
            startError = e;
            ready.countDown();
            return;
        }
        ready.countDown();
        while (!socket.isClosed()) {
            try {
                final Socket client = socket.accept();
                pool.execute(() -> serve(client));
            } catch (IOException e) {
                if (socket.isClosed()) return;
            }
        }
    }

    private void serve(Socket client) {
        try (Socket c = client) {
            c.setSoTimeout(20000);
            InputStream in = new BufferedInputStream(c.getInputStream());
            OutputStream out = new BufferedOutputStream(c.getOutputStream());
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String header;
            while ((header = readLine(in)) != null && !header.isEmpty()) {
                // Headers are not needed: every response is a full, uncached file.
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                send(out, 405, "text/plain; charset=utf-8", bytes("Method not allowed"), method, null);
                return;
            }

            String path = parts[1];
            int cut = path.indexOf('?');
            if (cut >= 0) path = path.substring(0, cut);
            cut = path.indexOf('#');
            if (cut >= 0) path = path.substring(0, cut);
            path = URLDecoder.decode(path.replace("+", "%2B"), "UTF-8");
            if (!path.startsWith("/") || path.contains("..") || path.contains("\\")) {
                send(out, 403, "text/plain; charset=utf-8", bytes("Forbidden"), method, null);
                return;
            }
            if (path.endsWith("/")) path += "index.html";

            byte[] body = read(ROOT + path);
            if (body == null) {
                // A folder without the trailing slash: redirect so relative links keep working.
                if (read(ROOT + path + "/index.html") != null) {
                    send(out, 301, "text/plain; charset=utf-8", bytes("Moved"), method, path + "/");
                } else {
                    send(out, 404, "text/plain; charset=utf-8", bytes("Not found: " + path), method, null);
                }
                return;
            }
            send(out, 200, mimeType(path), body, method, null);
        } catch (IOException ignored) {
            // The page closed the connection early; nothing to do.
        }
    }

    private byte[] read(String name) {
        try (InputStream is = assets.open(name)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[32 * 1024];
            int n;
            while ((n = is.read(chunk)) > 0) buffer.write(chunk, 0, n);
            return buffer.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static void send(OutputStream out, int code, String type, byte[] body, String method, String location)
            throws IOException {
        StringBuilder h = new StringBuilder(256);
        h.append("HTTP/1.1 ").append(code).append(' ').append(reason(code)).append("\r\n");
        h.append("Content-Type: ").append(type).append("\r\n");
        h.append("Content-Length: ").append(body.length).append("\r\n");
        h.append("Cache-Control: no-cache\r\n");
        h.append("X-Content-Type-Options: nosniff\r\n");
        h.append("Connection: close\r\n");
        if (location != null) h.append("Location: ").append(location).append("\r\n");
        h.append("\r\n");
        out.write(h.toString().getBytes(StandardCharsets.US_ASCII));
        if (!"HEAD".equals(method)) out.write(body);
        out.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') return sb.toString();
            if (b != '\r') sb.append((char) b);
            if (sb.length() > 16384) throw new IOException("Line too long");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 301: return "Moved Permanently";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            default: return "Method Not Allowed";
        }
    }

    private static final Map<String, String> TYPES = new HashMap<>();
    static {
        TYPES.put("html", "text/html; charset=utf-8");
        TYPES.put("htm", "text/html; charset=utf-8");
        TYPES.put("js", "text/javascript; charset=utf-8");
        TYPES.put("mjs", "text/javascript; charset=utf-8");
        TYPES.put("css", "text/css; charset=utf-8");
        TYPES.put("json", "application/json; charset=utf-8");
        TYPES.put("webmanifest", "application/manifest+json; charset=utf-8");
        TYPES.put("wasm", "application/wasm");
        TYPES.put("svg", "image/svg+xml");
        TYPES.put("png", "image/png");
        TYPES.put("jpg", "image/jpeg");
        TYPES.put("jpeg", "image/jpeg");
        TYPES.put("gif", "image/gif");
        TYPES.put("webp", "image/webp");
        TYPES.put("ico", "image/x-icon");
        TYPES.put("woff", "font/woff");
        TYPES.put("woff2", "font/woff2");
        TYPES.put("ttf", "font/ttf");
        TYPES.put("otf", "font/otf");
        TYPES.put("txt", "text/plain; charset=utf-8");
        TYPES.put("md", "text/plain; charset=utf-8");
        TYPES.put("py", "text/plain; charset=utf-8");
        TYPES.put("csv", "text/csv; charset=utf-8");
        TYPES.put("xml", "application/xml; charset=utf-8");
        TYPES.put("pdf", "application/pdf");
        TYPES.put("zip", "application/zip");
        TYPES.put("mp3", "audio/mpeg");
        TYPES.put("mp4", "video/mp4");
    }

    private static String mimeType(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String type = TYPES.get(path.substring(dot + 1).toLowerCase(Locale.ROOT));
        return type != null ? type : "application/octet-stream";
    }
}
