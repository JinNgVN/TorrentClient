import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.*;
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

    //for udp request only
    private static final Random RANDOM = new Random();
    private static final int key = RANDOM.nextInt();
    public TrackerConnection(String announceUrl, byte[] peerId, byte[] infoHash) {
        this.annouceUrl = announceUrl;
        this.peerId = peerId;
        this.infoHash = infoHash;
    }

    public void sendRequest() {
        if (annouceUrl.startsWith("udp://")) {
            sendUdpRequest(TrackerEvent.STARTED);
        } else if (annouceUrl.startsWith("http://") | annouceUrl.startsWith("https://")) {
//            sendHttpRequest(TrackerEvent.STARTED); //TODO try to handle error: port fails, timeout, ...
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
//        var httpClient = SingletonHttpClient.getClient();
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
            System.out.println(decoded);
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
        //TODO add more checks: check if the url already had '?' or not
        //TODO the bittorrent specification leaves out compact, no peer id, numwant, key, trackerID and ip. Decide what to do with it
        StringBuilder bd = new StringBuilder(annouceUrl);
        bd.append("?info_hash=").append(urlEncodeBytes(infoHash))
                .append("&peer_id=").append(urlEncodeBytes(peerId))
                .append("&port=").append(port)
                .append("&uploaded=").append(uploaded)
                .append("&downloaded=").append(downloaded)
                .append("&left=").append(left)
                .append("&event=").append(event.getStringValue());
        return bd.toString();
    }

    private String urlEncodeBytes(byte[] bytes) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : bytes) {
            encoded.append('%').append(String.format("%02x", b & 0xff));
        }
        return encoded.toString();
    }

    private void sendUdpRequest(TrackerEvent event) {
        System.out.println(annouceUrl);
        InetSocketAddress address = parseUdpAnnounceUrl(annouceUrl);
        //prepare packet for connection request
        ByteBuffer connectRequestBuffer = ByteBuffer.allocate(16);
        connectRequestBuffer.order(ByteOrder.BIG_ENDIAN);
        connectRequestBuffer.putLong(0x41727101980L); //magic constant
        connectRequestBuffer.putInt(0); // action = connect = 0
        var connectTransactionId = RANDOM.nextInt();
        connectRequestBuffer.putInt(connectTransactionId);

        //sending connect request
        connectRequestBuffer.flip();
        DatagramChannel channel = SingletonDatagramChannel.getInstance();
        try {
            channel.send(connectRequestBuffer, address);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //receive response
        ByteBuffer responseConnectBuffer = ByteBuffer.allocate(16);
        responseConnectBuffer.clear();
        System.out.println(Arrays.toString(responseConnectBuffer.array()));
        try {
            channel.receive(responseConnectBuffer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //parse response
        responseConnectBuffer.flip();
        //check length
        if (responseConnectBuffer.remaining() < 16) {
            throw new TrackerException("Connect Request: Received buffer is smaller than 16 bytes");
        }
        //check action
        if (responseConnectBuffer.getInt() != 0 ) {
            throw new TrackerException("Connect Request: Received different action other than 0");
        }
        //check transactionId
        if (responseConnectBuffer.getInt() != connectTransactionId) {
            throw new TrackerException("Connect Request: Received different transactionId");
        }
        // acquire connectionId
        long connectionId = responseConnectBuffer.getLong();

        //prepare packet for announce request
        ByteBuffer announceBuffer = ByteBuffer.allocate(98);
        announceBuffer.order(ByteOrder.BIG_ENDIAN);

        announceBuffer.putLong(connectionId);
        announceBuffer.putInt(1); //action = announce = 1
        var announceTransactionId = RANDOM.nextInt();
        announceBuffer.putInt(announceTransactionId);
        announceBuffer.put(infoHash);
        announceBuffer.put(peerId);
        announceBuffer.putLong(downloaded);
        announceBuffer.putLong(left);
        announceBuffer.putLong(uploaded);
        announceBuffer.putInt(event.getIntValue());
        announceBuffer.putInt(0); //TODO This is ip address, default to 0, need to study this more
        announceBuffer.putInt(key); //key for all session of this torrent
        announceBuffer.putInt(-1); //numWant, default to -1
        announceBuffer.putShort((short) port); //TODO need to have an instance variable for this in UDPManager

        //sending announce request
        announceBuffer.flip();
        try {
            channel.send(announceBuffer, address);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //receive response
        ByteBuffer responseAnnounceBuffer = ByteBuffer.allocate(1024);
        responseAnnounceBuffer.clear();
        try {
            channel.receive(responseAnnounceBuffer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //parse response
        if (responseAnnounceBuffer.remaining() < 20) {
            throw new TrackerException("Announce Request: Received buffer is smaller than 20 bytes");
        }
        //check action
        if (responseAnnounceBuffer.getInt() != 1) {
            throw new TrackerException("Announce Request: Received different action other than 1");
        }
        //check transactionId
        if (responseAnnounceBuffer.getInt() != announceTransactionId) {
            throw new TrackerException("Announce Request: Received different transactionId");
        }
        int interval = responseAnnounceBuffer.getInt(); //TODO assign to instance variable instead - PEER_SIZE = 6
        int leechers = responseAnnounceBuffer.getInt();
        int seeders = responseAnnounceBuffer.getInt();
        int remainingBytes = responseAnnounceBuffer.remaining();
        if (remainingBytes % 6 != 0) { //TODO move '6' to static instance variable
            throw new TrackerException("Announce Request: Peer data length is not multiple of 6 bytes");
        }
        int numPeers = remainingBytes / 6;

        // Parse peers
        List<Peer> peers = new ArrayList<>();
        for (int i = 0; i < numPeers; i++) {
            // Read IP (4 bytes)
            String ip = String.format("%d.%d.%d.%d",
                    responseAnnounceBuffer.get() & 0xFF,
                    responseAnnounceBuffer.get() & 0xFF,
                    responseAnnounceBuffer.get() & 0xFF,
                    responseAnnounceBuffer.get() & 0xFF);

            // Read port (2 bytes)
            int port = ((responseAnnounceBuffer.get() & 0xFF) << 8) |
                    (responseAnnounceBuffer.get() & 0xFF);

            peers.add(new Peer(ip, port));
        }
        System.out.println("Interval: " + interval);
        System.out.println("Leechers: " + leechers);
        System.out.println("Seeders: " + seeders);
        System.out.println("Peers: " +peers);
        //sending udp request
    }

    private InetSocketAddress parseUdpAnnounceUrl(String announceUrl) {
        //remove udp:// and /announce part
        String cleanedUrl = announceUrl
                .replace("udp://", "")
                .replace("/announce", "");

        String[] parts = cleanedUrl.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        return new InetSocketAddress(host, port);
    }



    private void sendWsRequest() {
//        System.out.println("WS Request: " + annouceUrl);

    }
}
