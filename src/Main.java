import com.dampcake.bencode.Bencode;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Main {

    private static final int CONNECT_ACTION = 0;
    private static final int ANNOUNCE_ACTION = 1;

    private static final long PROTOCOL_ID = 0x41727101980L;

    public static void sendAnnounceRequest(String url,long connectionId, byte[] infoHash, byte[] peerId) throws IOException {
        //prepare packet for announce requets
        int transactionId = (int) (Math.random() * Integer.MAX_VALUE);

        ByteBuffer announceRequestDataBuffer = ByteBuffer.allocate(98);
        announceRequestDataBuffer.putLong(connectionId);
        announceRequestDataBuffer.putInt(ANNOUNCE_ACTION);
        announceRequestDataBuffer.putInt(transactionId);
        announceRequestDataBuffer.put(infoHash);
        announceRequestDataBuffer.put(peerId);
        announceRequestDataBuffer.putLong(0); //downloaded
        announceRequestDataBuffer.putLong(7931777550L); //left
        announceRequestDataBuffer.putLong(0); //uploaded
        announceRequestDataBuffer.putInt(2); //event

        URI uri = URI.create(url);
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName(uri.getHost());
        int port = uri.getPort();

        // Step 1: Send announce request
        byte[] announceRequestData = announceRequestDataBuffer.array();
        DatagramPacket connectRequestPacket = new DatagramPacket(announceRequestData, announceRequestData.length, address, port);
        socket.send(connectRequestPacket);


        // Step 2: receive response
        byte[] announceResponseData = new byte[1024];
        DatagramPacket connectResponsePacket = new DatagramPacket(announceResponseData, announceResponseData.length);
        socket.receive(connectResponsePacket);

        ByteBuffer buffer = ByteBuffer.wrap(announceResponseData);
        int action = buffer.getInt();
        int responseTransactionId = buffer.getInt();
        int interval = buffer.getInt();
        int leechers = buffer.getInt();
        int seeders = buffer.getInt();

        System.out.println("Action: " + action);
        System.out.println("Transaction ID: " + responseTransactionId);
        System.out.println("Interval: " + interval);
        System.out.println("Leechers: " + leechers);
        System.out.println("Seeders: " + seeders);
        if (action != 1 || responseTransactionId != transactionId) {
            System.out.println("Wrong action or transaction id in announce request");
            return;
        }

        Map<Integer, Short> peerList = new HashMap<>();
        while (buffer.remaining() >= 6) {
            int peerIp = buffer.getInt();
            short peerPort = buffer.getShort();
            peerList.put(peerIp, peerPort);
        }

        System.out.println(peerList);

    }


    public static long sendConnectionRequest(String url) throws IOException {
        URI uri = URI.create(url);
        int transactionId = (int) (Math.random() * Integer.MAX_VALUE);
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName(uri.getHost());
        int port = uri.getPort();

        // Step 1: Send connect request
        byte[] connectRequestData = createConnectRequestPacket(transactionId);
        DatagramPacket connectRequestPacket = new DatagramPacket(connectRequestData, connectRequestData.length, address, port);
        socket.send(connectRequestPacket);

        // Step 2: Receive connect response
        ByteBuffer responseBuffer = ByteBuffer.allocate(16);
        byte[] connectResponseData = responseBuffer.array();
        DatagramPacket connectResponsePacket = new DatagramPacket(connectResponseData, connectResponseData.length);
        socket.receive(connectResponsePacket);

        // Check the received packet
        int action = responseBuffer.getInt();
        int receivedTransactionId = responseBuffer.getInt();
        System.out.println("Action: " + action);
        System.out.println("Transanction id: " + receivedTransactionId);
        System.out.println("my transaction id:" + transactionId);
        if (receivedTransactionId != transactionId || action != CONNECT_ACTION) {
            System.out.println("Wrong transaction id in connection request or transaction id for connection request!");
            return -1;
        }
        return responseBuffer.getLong();
    }

    private static byte[] createConnectRequestPacket(int transactionId) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(PROTOCOL_ID);
        buffer.putInt(CONNECT_ACTION);
        buffer.putInt(transactionId); // Transaction ID
        return buffer.array();
    }


    public static byte[] getInfoHash(Map<String, Object> content) {
        var b = new Bencode();
        var infoDecoded = (Map<String, Object>) content.get("info");
        var infoEncoded = b.encode(infoDecoded);
        System.out.println(new String(infoEncoded, b.getCharset()));
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
        return sb.toString().getBytes();
    }

    public static byte[] generatePeerId() {
        byte[] peerId = new byte[20];

        // Set the prefix
        byte[] prefix = ("-" + "JV"+ "0001").getBytes(StandardCharsets.UTF_8);
        System.arraycopy(prefix, 0, peerId, 0, prefix.length);

        // Generate random bytes for the remainder
        SecureRandom random = new SecureRandom();
        random.nextBytes(Arrays.copyOfRange(peerId, prefix.length, 20));

        return peerId;
    }

    public static void main(String[] args) {
        Parser p = new Parser();
        p.parse("file.torrent");

        var peerId = generatePeerId();
        try {
            long connectionId = sendConnectionRequest(p.getAnnounce());
            sendAnnounceRequest(p.getAnnounce(),connectionId, p.getHashInfoContent(), peerId);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


//      String encodedParams = "?info_hash=" + infoHash + "&peer_id=" + peerId + "&port=6881&uploaded=0&downloaded=0&left=1000&compact=1&event=started";
//            System.out.println(trackerUrl + encodedParams);

    }
}