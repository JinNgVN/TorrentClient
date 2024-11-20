import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

public class TrackerConnection {
    private int minInterval;
    private int interval;
    private long uploaded = 0;
    private long downloaded = 0;
    private long left;
    private String trackerId;
    private int numWant;
    private int numSeeders;
    private int numLeechers;
    private List<Peer> peers = new ArrayList<Peer>();
    private HttpClient httpClient;
    private final int port = 6881;

    private final byte[] peerId;
    private final String annouceUrl;
    private final byte[] infoHash;
    private Status status;


    public TrackerConnection(String announceUrl, byte[] peerId, byte[] infoHash) {
        this.annouceUrl = announceUrl;
        this.peerId = peerId;
        this.infoHash = infoHash;
    }

    public void sendRequest() {
        if (annouceUrl.startsWith("udp://")) {
            sendUdpRequest();
        } else if (annouceUrl.startsWith("http://") | annouceUrl.startsWith("https://")) {
            sendHttpRequest(TrackerEvent.STARTED); //TODO try to handle error: port fails, timeout, ...
        } else if (annouceUrl.startsWith("wss://") | annouceUrl.startsWith("ws://")) {
            sendWsRequest();

        }
        //TODO add a new throw for wrong protocol besides udp, http, https, ws or wss
//        else {
//            throw errror
//        }

    }

    // send request -> (if error, handle it) -> receive response -> parse response & get all information -> store in a class TrackerResponseState ->
    // -> schedule to send announce at every x seconds -> receive response and parse it -> update TrackerResponseState
    // update all the states before sending request: uploaded, downloaded, left, event
    //
    //
    private void sendHttpRequest(TrackerEvent event) {
        var httpClient = SingletonHttpClient.getClient();
        String fullUrl = buildFullUrl(event);

        var request = HttpRequest.newBuilder()
                .GET()
                .header("User-Agent", "BitTorrent/1.0")
                .uri(URI.create(fullUrl))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(this::handleResponse)
                .exceptionally(throwable -> {
                    // Unwrap CompletionException
                    Throwable cause = throwable instanceof CompletionException ?
                            throwable.getCause() : throwable;
                    System.err.println("Error type: " + cause.getClass().getSimpleName());
                    System.err.println("Error message: " + cause.getMessage());
                    cause.printStackTrace();  // Print full stack trace
                    return null;
                });

        // Add delay to keep program running
        try {
            Thread.sleep(5000);  // Wait 5 seconds for response
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleResponse(HttpResponse<byte[]> response) {
        if (response.statusCode() != 200) {
            throw new TrackerException("HTTP error: " + response.statusCode());
        }
        try {
            var decoded = (Map<String, Object>) Bencode.decode(response.body());
            if (decoded.containsKey("failure reason")) {
                throw new TrackerException("Failure reason: " + decoded.get("failure reason"));
            }
            interval = (int) decoded.get("interval");
            minInterval = (int) decoded.get("min interval"); //WARN can be null
            trackerId = (String) decoded.get("tracker id"); //WARN can be null
            numSeeders = (int) decoded.get("complete");
            numLeechers = (int) decoded.get("incomplete");

            byte[] compactPeers = ((String) decoded.get("peers")).getBytes(); //TODO assume using binary model, need to handle dictionary model
            if (compactPeers.length % 6 != 0) {
                throw new TrackerException("Peers length is not a multiple of 6");
            }
            for (int i = 0; i < compactPeers.length; i += 6) {
                // Convert 4 bytes to IP address
                String ip = String.format("%d.%d.%d.%d",
                        compactPeers[i] & 0xFF,
                        compactPeers[i + 1] & 0xFF,
                        compactPeers[i + 2] & 0xFF,
                        compactPeers[i + 3] & 0xFF);

                // Convert 2 bytes to port number
                int port = ((compactPeers[i + 4] & 0xFF) << 8) |
                        (compactPeers[i + 5] & 0xFF);

                peers.add(new Peer(ip, port));
            }

        } catch (ClassCastException e) {
            throw new BencodeException(e.getMessage(), e);
        }
    }

    private String buildFullUrl(TrackerEvent event) {
        System.out.println("build full url");
        //TODO add more checks: check if the url already had '?' or not
        //TODO the bittorrent specification leaves out compact, no peer id, numwant, key, trackerID and ip. Decide what to do with it
        StringBuilder bd = new StringBuilder(annouceUrl);
        bd.append("?info_hash=").append(urlEncodeBytes(infoHash))
                .append("&peer_id=").append(urlEncodeBytes(peerId))
                .append("&port=").append(port)
                .append("&uploaded=").append(uploaded)
                .append("&downloaded=").append(downloaded)
                .append("&left=").append(left)
                .append("&event=").append(event.getValue());
        return bd.toString();
    }

    private String urlEncodeBytes(byte[] bytes) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : bytes) {
            encoded.append('%').append(String.format("%02x", b & 0xff));
        }
        return encoded.toString();
    }

    private void sendUdpRequest() {
//        System.out.println("UDP Request: " + annouceUrl);

    }

    private void sendWsRequest() {
//        System.out.println("WS Request: " + annouceUrl);

    }
}
