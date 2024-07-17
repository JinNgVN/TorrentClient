import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

public class Main {

    private static final int CONNECT_ACTION = 0;
    private static final long PROTOCOL_ID = 0x41727101980L;

    public static void sendConnectionRequest(String url) throws IOException {
        URI uri = URI.create(url);
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName(uri.getHost());
        int port = uri.getPort();

        // Step 1: Send connect request
        byte[] connectRequestData = createConnectRequestPacket();
        DatagramPacket connectRequestPacket = new DatagramPacket(connectRequestData, connectRequestData.length, address, port);
        socket.send(connectRequestPacket);

        // Step 2: Receive connect response
        byte[] connectResponseData = new byte[16];
        DatagramPacket connectResponsePacket = new DatagramPacket(connectResponseData, connectResponseData.length);
        socket.receive(connectResponsePacket);
        System.out.println(Arrays.toString(connectResponseData));
    }

    private static byte[] createConnectRequestPacket() {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(PROTOCOL_ID);
        buffer.putInt(CONNECT_ACTION);
        buffer.putInt((int) (Math.random() * Integer.MAX_VALUE)); // Transaction ID
        return buffer.array();
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
            sendConnectionRequest(trackerUrl);

//            System.out.println(trackerUrl + encodedParams);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}