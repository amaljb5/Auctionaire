import com.sun.net.httpserver.*;
import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class AuctionServer {
    private static final AuctionManager auctionManager = new AuctionManager();
    private static AdminGUI adminGUI;

    public static void main(String[] args) throws IOException {
        SwingUtilities.invokeLater(() -> adminGUI = new AdminGUI(auctionManager));

        int port = 8081;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", new FileHandler("index.html"));
        server.createContext("/api/auctions", new AuctionsApiHandler());
        server.createContext("/api/bid", new BidApiHandler());
        server.createContext("/api/my-wins", new WinsApiHandler());
        server.createContext("/api/user-status", new UserStatusApiHandler());


        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("Server started on port " + port + ". Bidders can now connect.");
    }

    // --- HTTP Handlers for Bidders ---
    static class FileHandler implements HttpHandler {
        private final String filePath;
        FileHandler(String path) { this.filePath = path; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            File file = new File(filePath);
            if (!file.exists()) {
                String response = "404 Not Found: index.html missing.";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }
            exchange.sendResponseHeaders(200, file.length());
            try (OutputStream os = exchange.getResponseBody(); FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    static class AuctionsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = auctionManager.getActiveAuctionsAsJson();
            sendJsonResponse(exchange, response);
        }
    }
    
    // **FIXED:** Handler for user status now uses robust parsing
    static class UserStatusApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(query);
            String bidderName = params.get("bidderName");
            if (bidderName != null) {
                String response = auctionManager.getBidderStatusAsJson(bidderName);
                sendJsonResponse(exchange, response);
            } else {
                sendJsonResponse(exchange, "{}");
            }
        }
    }


    // **FIXED:** Handler for won items now uses robust parsing
    static class WinsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(query);
            String bidderName = params.get("bidderName");
             if (bidderName != null) {
                String response = auctionManager.getWonAuctionsAsJson(bidderName);
                sendJsonResponse(exchange, response);
            } else {
                 sendJsonResponse(exchange, "[]");
            }
        }
    }

    static class BidApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(readInputStream(exchange.getRequestBody()), StandardCharsets.UTF_8);
                Map<String, String> params = parseQuery(body);
                int auctionId = Integer.parseInt(params.get("auctionId"));
                String bidderName = params.get("bidderName");
                double bidAmount = Double.parseDouble(params.get("bidAmount"));

                String result = auctionManager.placeBid(auctionId, bidderName, bidAmount);
                sendTextResponse(exchange, result);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- Utility Methods ---
    private static void sendJsonResponse(HttpExchange exchange, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private static void sendTextResponse(HttpExchange exchange, String text) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        byte[] response = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
    
    private static byte[] readInputStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    // **FIXED:** Error handling for unsupported encoding is now robust
    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] entry = param.split("=");
                if (entry.length > 1) {
                    try {
                        result.put(URLDecoder.decode(entry[0], "UTF-8"), URLDecoder.decode(entry[1], "UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        // This should never happen with UTF-8, but if it does, it's a critical environment error.
                        throw new RuntimeException("FATAL: UTF-8 encoding not supported", e);
                    }
                }
            }
        }
        return result;
    }
}

class AuctionManager {
    private final List<Auction> allAuctions = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Bidder> bidders = new ConcurrentHashMap<>();
    private int nextAuctionId = 1;

    public synchronized void addAuction(String itemName, int durationSeconds, double startPrice, AdminGUI gui) {
        Auction auction = new Auction(nextAuctionId++, itemName, startPrice, durationSeconds, gui);
        allAuctions.add(auction);
        new Thread(auction).start();
    }
    
    public synchronized void stopAuction(int auctionId) {
        allAuctions.stream()
                   .filter(a -> a.getId() == auctionId && a.isActive())
                   .findFirst()
                   .ifPresent(Auction::stopAuction);
    }

    public synchronized String placeBid(int auctionId, String bidderName, double amount) {
        Bidder bidder = bidders.computeIfAbsent(bidderName.trim(), Bidder::new);

        if (bidder.getWallet() < amount) {
            return "Error: Insufficient funds.";
        }
        
        Optional<Auction> auctionOpt = allAuctions.stream()
            .filter(a -> a.getId() == auctionId)
            .findFirst();

        return auctionOpt.map(auction -> auction.placeBid(bidder, amount))
                         .orElse("Error: Auction not found.");
    }

    public List<Auction> getAllAuctions() {
        return new ArrayList<>(allAuctions);
    }
    
    public String getActiveAuctionsAsJson() {
        return toJson(allAuctions.stream().filter(Auction::isActive).collect(Collectors.toList()));
    }
    
    public String getWonAuctionsAsJson(String bidderName) {
        Bidder bidder = bidders.get(bidderName);
        if (bidder != null) {
            return bidder.getWonItemsAsJson();
        }
        return "[]";
    }
    
    public String getBidderStatusAsJson(String bidderName) {
        Bidder bidder = bidders.computeIfAbsent(bidderName.trim(), Bidder::new);
        return bidder.toJson();
    }
    
    private String toJson(List<Auction> auctionList) {
        return auctionList.stream()
                          .map(Auction::toJson)
                          .collect(Collectors.joining(", ", "[", "]"));
    }
}

class Auction implements Runnable {
    private final int id;
    private final String itemName;
    private final double startPrice;
    private volatile int remainingTime;
    private volatile boolean active = true;
    private volatile Bidder highestBidder;
    private volatile double highestBid;
    private final Map<String, Integer> bidCounts = new ConcurrentHashMap<>();
    
    private final AdminGUI gui;

    public Auction(int id, String itemName, double startPrice, int duration, AdminGUI gui) {
        this.id = id;
        this.itemName = itemName;
        this.startPrice = startPrice;
        this.highestBid = startPrice;
        this.remainingTime = duration;
        this.gui = gui;
    }
    
    public int getId() { return id; }
    public String getItemName() { return itemName; }
    public double getStartPrice() { return startPrice; }
    public double getHighestBid() { return highestBid; }
    public String getHighestBidderName() { return highestBidder != null ? highestBidder.getName() : "None"; }
    public int getRemainingTime() { return remainingTime; }
    public boolean isActive() { return active; }
    public String getStatus() {
        if (active) return "Active";
        if (highestBidder != null) return "Won by " + getHighestBidderName();
        return "Ended (No Bids)";
    }
    
    public void stopAuction() {
        if (active) {
            remainingTime = 0;
            // The auction thread will handle the end logic
        }
    }

    @Override
    public void run() {
        while (active && remainingTime > 0) {
            try {
                Thread.sleep(1000);
                if (active) {
                    remainingTime--;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                SwingUtilities.invokeLater(gui::updateAuctionList);
            }
        }
        if (active) {
            endAuction();
        }
    }

    private void endAuction() {
        active = false;
        if (highestBidder != null) {
            highestBidder.winAuction(itemName, highestBid);
            System.out.println("LOG: " + highestBidder.getName() + " won " + itemName + " for $" + highestBid);
        }
        System.out.println("LOG: Auction for " + itemName + " ended.");
        SwingUtilities.invokeLater(gui::updateAuctionList);
    }
    
    public synchronized String placeBid(Bidder bidder, double amount) {
        if (!active) return "Error: Auction has ended.";
        if (amount <= highestBid) return "Error: Your bid must be higher than the current highest bid.";
        
        int currentBids = bidCounts.getOrDefault(bidder.getName(), 0);
        if (currentBids >= 10) return "Error: You have reached the maximum of 10 bids for this item.";

        highestBid = amount;
        highestBidder = bidder;
        bidCounts.put(bidder.getName(), currentBids + 1);
        SwingUtilities.invokeLater(gui::updateAuctionList);
        return "Success: Your bid has been placed!";
    }
    
    public String toJson() {
        return String.format(Locale.US,
            "{\"id\":%d, \"itemName\":\"%s\", \"highestBid\":%.2f, \"highestBidder\":\"%s\", \"remainingTime\":%d, \"status\":\"%s\"}",
            id, itemName, highestBid, getHighestBidderName(), remainingTime, getStatus()
        );
    }
}

class Bidder {
    private final String name;
    private double wallet = 10000.00;
    private final List<Map<String, String>> wonItems = Collections.synchronizedList(new ArrayList<>());

    public Bidder(String name) { this.name = name; }
    public String getName() { return name; }
    public double getWallet() { return wallet; }

    public synchronized void winAuction(String itemName, double cost) {
        if (wallet >= cost) {
            wallet -= cost;
            Map<String, String> wonItem = new HashMap<>();
            wonItem.put("itemName", itemName);
            wonItem.put("cost", String.format(Locale.US, "%.2f", cost));
            wonItems.add(wonItem);
        }
    }
    
    public String getWonItemsAsJson() {
        // Simple JSON serialization for the list of maps
        return wonItems.stream()
            .map(item -> String.format("{\"itemName\":\"%s\", \"cost\":%s}", item.get("itemName"), item.get("cost")))
            .collect(Collectors.joining(", ", "[", "]"));
    }
    
    public String toJson() {
        return String.format(Locale.US, "{\"name\":\"%s\", \"wallet\":%.2f}", name, wallet);
    }
}

