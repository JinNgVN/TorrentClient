import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class Main {
    private static final long CONNECTION_ID = 0x41727101980L;
    private static final int ACTION_CONNECT = 0;
    private static final int ACTION_ANNOUNCE = 1;

    public static void sendTrackerRequest(String url) throws Exception {
        URI uri = new URI(url);
        if (!"udp".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only UDP protocol is supported");
        }

        InetAddress address = InetAddress.getByName(uri.getHost());
        int port = uri.getPort();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(15000); // 15 seconds timeout

            // Send connection request and get connection ID
            long connectionId = sendConnectionRequest(socket, address, port);

            // Send announce request
            sendAnnounceRequest(socket, address, port, connectionId, uri.getQuery());

        } catch (IOException e) {
            throw new RuntimeException("Error communicating with tracker", e);
        }
    }

    private static long sendConnectionRequest(DatagramSocket socket, InetAddress address, int port) throws IOException {
        ByteBuffer requestBuffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);
        requestBuffer.putLong(CONNECTION_ID);
        requestBuffer.putInt(ACTION_CONNECT);
        requestBuffer.putInt(new Random().nextInt());

        DatagramPacket packet = new DatagramPacket(requestBuffer.array(), requestBuffer.capacity(), address, port);
        socket.send(packet);

        byte[] responseBuffer = new byte[16];
        packet = new DatagramPacket(responseBuffer, responseBuffer.length);
        socket.receive(packet);

        ByteBuffer responseByteBuffer = ByteBuffer.wrap(packet.getData()).order(ByteOrder.BIG_ENDIAN);
        responseByteBuffer.getInt(); // action
        responseByteBuffer.getInt(); // transaction_id
        return responseByteBuffer.getLong();
    }

    private static void sendAnnounceRequest(DatagramSocket socket, InetAddress address, int port, long connectionId, String query) throws IOException {
        ByteBuffer requestBuffer = ByteBuffer.allocate(98).order(ByteOrder.BIG_ENDIAN);
        requestBuffer.putLong(connectionId);
        requestBuffer.putInt(ACTION_ANNOUNCE);
        requestBuffer.putInt(new Random().nextInt());

        // Parse query string and add appropriate fields
        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2) {
                if (keyValue[0].equals("info_hash")) {
                    requestBuffer.put(urlDecodeBytes(keyValue[1]));
                } else if (keyValue[0].equals("peer_id")) {
                    requestBuffer.put(keyValue[1].getBytes());
                }
                // Add other parameters as needed
            }
        }

        // Fill the rest with default values
        requestBuffer.putLong(0); // downloaded
        requestBuffer.putLong(1000); // left
        requestBuffer.putLong(0); // uploaded
        requestBuffer.putInt(2); // event (2 = started)
        requestBuffer.putInt(0); // IP address (default)
        requestBuffer.putInt(new Random().nextInt()); // key
        requestBuffer.putInt(-1); // num_want (-1 = default)
        requestBuffer.putShort((short) 6881); // port

        DatagramPacket packet = new DatagramPacket(requestBuffer.array(), requestBuffer.capacity(), address, port);
        socket.send(packet);

        byte[] responseBuffer = new byte[1024];
        packet = new DatagramPacket(responseBuffer, responseBuffer.length);
        socket.receive(packet);

        parseAnnounceResponse(packet.getData());
    }

    private static void parseAnnounceResponse(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int action = buffer.getInt();
        int transactionId = buffer.getInt();
        int interval = buffer.getInt();
        int leechers = buffer.getInt();
        int seeders = buffer.getInt();
        System.out.println("Interval: " + interval);
        System.out.println("Leechers: " + leechers);
        System.out.println("Seeders: " + seeders);

        while (buffer.remaining() >= 6) {
            int ip = buffer.getInt();
            short port = buffer.getShort();
            System.out.println("Peer: " + String.format("%d.%d.%d.%d", (ip >> 24) & 0xFF, (ip >> 16) & 0xFF, (ip >> 8) & 0xFF, ip & 0xFF) + ":" + (port & 0xFFFF));
        }
    }

    private static byte[] urlDecodeBytes(String s) {
        ByteBuffer buffer = ByteBuffer.allocate(s.length() / 3);
        for (int i = 0; i < s.length(); i += 3) {
            buffer.put((byte) Integer.parseInt(s.substring(i + 1, i + 3), 16));
        }
        return buffer.array();
    }
    public static void sendHttpRequest(String url) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = null;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Synchronous Response Status Code: " + response.statusCode());
        System.out.println("Synchronous Response Body: " + response.body());

    }

    public static String getInfoHash(Map<String, Object> content) {
        var b = new Bencode();
        var infoDecoded = (Map<String, Object>) content.get("info");
        var infoEncoded = b.encode(infoDecoded);
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        var infoHash = md.digest(infoEncoded);
        StringBuilder sb = new StringBuilder();
        for (byte info : infoHash) {
            sb.append(String.format("%%%02X", info));
        }
        return sb.toString();
    }

    public static String generatePeerId() {
        StringBuilder peerId = new StringBuilder(20);

        // 1. Add a prefix to identify your client (e.g., "-JA0001-")
        peerId.append("-JA0001-");

        // 2. Fill the rest with random numbers
        Random random = new Random();
        for (int i = peerId.length(); i < 20; i++) {
            peerId.append(random.nextInt(10));
        }

        return peerId.toString();
    }

    public static String urlEncodePeerId(String peerId) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : peerId.getBytes(StandardCharsets.ISO_8859_1)) {
            if ((b >= '0' && b <= '9') || (b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || b == '-' || b == '.' || b == '_' || b == '~') {
                encoded.append((char) b);
            } else {
                encoded.append(String.format("%%%02X", b));
            }
        }
        return encoded.toString();
    }

    public static void main(String[] args) {
        Bencode b = new Bencode();
        try (FileInputStream fis = new FileInputStream(new File("file.torrent"))) {
            var rawContent = fis.readAllBytes();
            Map<String, Object> content = b.decode(rawContent, Type.DICTIONARY);
            var infoHash = getInfoHash(content);
            var peerId = urlEncodePeerId(generatePeerId());
            String trackerUrl = (String) content.get("announce");
            String encodedParams = "?info_hash=" + infoHash + "&peer_id=" + peerId + "&port=6881&uploaded=0&downloaded=0&left=1000&compact=1&event=started";
//            sendHttpRequest(trackerUrl + encodedParams);
            sendTrackerRequest(trackerUrl+ encodedParams);
//            System.out.println(trackerUrl + encodedParams);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}