package de.winniepat.vaultMesh.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.winniepat.vaultMesh.StorageManager;
import de.winniepat.vaultMesh.StorageNetwork;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class WebServer {

    private final JavaPlugin plugin;
    private final StorageManager manager;
    private final String host;
    private final int port;
    private final String token;

    private HttpServer server;
    private ExecutorService executor;

    public WebServer(JavaPlugin plugin, StorageManager manager,
                     String host, int port, String token) {
        this.plugin = plugin;
        this.manager = manager;
        this.host = host;
        this.port = port;
        this.token = token == null ? "" : token.trim();
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        executor = Executors.newFixedThreadPool(2);
        server.setExecutor(executor);
        server.createContext("/", this::handleIndex);
        server.createContext("/api/networks", this::handleApi);
        server.start();

        String suffix = token.isEmpty() ? "" : "?token=" + token;

        plugin.getLogger().info("VaultMesh web map running at http://"
                + host + ":" + port + "/" + suffix);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }

        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private boolean authorized(HttpExchange exchange) {
        if (token.isEmpty()) {
            return true;
        }

        String provided = queryParam(exchange, "token");

        if (provided == null) {
            provided = exchange.getRequestHeaders().getFirst("X-VaultMesh-Token");
        }

        return token.equals(provided);
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed");
            return;
        }

        if (!authorized(exchange)) {
            respond(exchange, 401, "text/plain; charset=utf-8", "Unauthorized");
            return;
        }

        byte[] body;

        try (InputStream in = plugin.getResource("web/index.html")) {
            body = in == null
                    ? "<!doctype html><h1>VaultMesh</h1><p>UI resource missing.</p>"
                    .getBytes(StandardCharsets.UTF_8)
                    : in.readAllBytes();
        }

        respond(exchange, 200, "text/html; charset=utf-8", body);
    }

    private void handleApi(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "application/json; charset=utf-8",
                    "{\"error\":\"method_not_allowed\"}");
            return;
        }

        if (!authorized(exchange)) {
            respond(exchange, 401, "application/json; charset=utf-8",
                    "{\"error\":\"unauthorized\"}");
            return;
        }

        try {
            respond(exchange, 200, "application/json; charset=utf-8", buildSnapshot());
        } catch (Exception exception) {
            plugin.getLogger().warning("VaultMesh web snapshot failed: " + exception);
            respond(exchange, 500, "application/json; charset=utf-8",
                    "{\"error\":\"snapshot_failed\"}");
        }
    }

    private String buildSnapshot() throws Exception {
        if (Bukkit.isPrimaryThread()) {
            return snapshotJson();
        }

        CompletableFuture<String> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                future.complete(snapshotJson());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });

        return future.get(15, TimeUnit.SECONDS);
    }

    private String snapshotJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"generatedAt\":").append(System.currentTimeMillis());
        json.append(",\"pageSize\":").append(StorageManager.PAGE_SIZE);
        json.append(",\"networks\":[");

        boolean first = true;

        for (StorageNetwork network : manager.getNetworks().values()) {
            if (!first) {
                json.append(',');
            }

            first = false;
            appendNetwork(json, network);
        }

        json.append("]}");
        return json.toString();
    }

    private void appendNetwork(StringBuilder json, StorageNetwork network) {
        int linked = network.getLinkedContainers().size();
        int capacity = network.getMaxContainers();
        int totalSlots = linked * 27;
        int pages = Math.max(1,
                (totalSlots + StorageManager.PAGE_SIZE - 1) / StorageManager.PAGE_SIZE);

        json.append("{\"name\":").append(quote(network.getName()));
        json.append(",\"linked\":").append(linked);
        json.append(",\"capacity\":").append(capacity);
        json.append(",\"pages\":").append(pages);
        json.append(",\"totalSlots\":").append(totalSlots);
        json.append(",\"public\":").append(network.isPublic());
        json.append(",\"owner\":").append(quote(network.getOwnerName()));
        json.append(",\"members\":").append(network.getMembers().size());
        json.append(",\"storedItems\":").append(network.getStoredItems());
        json.append(",\"openCount\":").append(network.getOpenCount());
        json.append(",\"created\":").append(network.getCreatedTime());
        json.append(",\"lastOpened\":").append(network.getLastOpened());
        json.append(",\"containers\":[");

        boolean first = true;

        for (String key : network.getLinkedContainers()) {
            if (!first) {
                json.append(',');
            }

            first = false;
            appendContainer(json, key);
        }

        json.append("]}");
    }

    private void appendContainer(StringBuilder json, String key) {
        String[] parts = key.split(",", 4);

        if (parts.length != 4) {
            json.append("{\"invalid\":true}");
            return;
        }

        String type = manager.containerTypeName(key);

        json.append("{\"world\":").append(quote(parts[0]));
        json.append(",\"x\":").append(parts[1]);
        json.append(",\"y\":").append(parts[2]);
        json.append(",\"z\":").append(parts[3]);
        json.append(",\"type\":").append(quote(type == null ? "UNKNOWN" : type));
        json.append(",\"loaded\":").append(type != null);
        json.append(",\"slots\":27}");
    }

    private void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        respond(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private void respond(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();

        if (query == null || query.isEmpty()) {
            return null;
        }

        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');

            if (eq < 0) {
                continue;
            }

            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);

            if (key.equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }

        return null;
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }

        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }

        sb.append('"');
        return sb.toString();
    }
}
