import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class UdpTrackerClient {
    private final byte[] peerId;
    private final String annouceUrl;
    private final byte[] infoHash;
    private List<Peer> peers = new ArrayList<>();
    private long uploaded = 0;
    private long downloaded = 0;
    private long left;


    private static final int port = 6881;

    private static final Random RANDOM = new Random();
    private final int key = RANDOM.nextInt();

    //constant
    //connect request
    private static final int CONNECT_REQUEST_BUFFER_SIZE = 16;
    private final ByteBuffer connectRequestBuffer = ByteBuffer.allocate(CONNECT_REQUEST_BUFFER_SIZE);

    //connect response
    private static final int CONNECT_RESPONSE_BUFFER_SIZE = 16;
    private final ByteBuffer responseConnectBuffer = ByteBuffer.allocate(CONNECT_RESPONSE_BUFFER_SIZE);

    //announce request
    private static final int ANNOUNCE_REQUEST_BUFFER_SIZE = 98;
    private final ByteBuffer announceBuffer = ByteBuffer.allocate(ANNOUNCE_REQUEST_BUFFER_SIZE);

    //announce response
    private static final int INITIAL_ANNOUNCE_RESPONSE_BUFFER_SIZE = 1024;
    private static final int MINIMUM_ANNOUNCE_RESPONSE_BUFFER_SIZE = 20;
    private final ByteBuffer responseAnnounceBuffer = ByteBuffer.allocate(INITIAL_ANNOUNCE_RESPONSE_BUFFER_SIZE);

    private static final int ACTION_CONNECT = 0;
    private static final int ACTION_ANNOUNCE = 1;
    private static final long MAGIC_CONSTANT = 0x41727101980L;
    private static final int DEFAULT_NUMWANT = -1;
    private static final int PEER_LENGTH = 6;


    private final DatagramChannel channel;


    public UdpTrackerClient(String announceUrl, byte[] peerId, byte[] infoHash) throws IOException {
        this.annouceUrl = announceUrl;
        this.peerId = peerId;
        this.infoHash = infoHash;
        channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.connect(parseUdpAnnounceUrl(announceUrl));
    }

    private void sendUdpRequest(TrackerEvent event) {
        System.out.println(annouceUrl);
        InetSocketAddress address = parseUdpAnnounceUrl(annouceUrl);
        //prepare packet for connection request
        connectRequestBuffer.order(ByteOrder.BIG_ENDIAN);
        connectRequestBuffer.putLong(MAGIC_CONSTANT); //magic constant
        connectRequestBuffer.putInt(ACTION_CONNECT); // action = connect = 0
        var connectTransactionId = RANDOM.nextInt();
        connectRequestBuffer.putInt(connectTransactionId);

        //sending connect request
        connectRequestBuffer.flip();
        try {
            channel.send(connectRequestBuffer, address);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        connectRequestBuffer.rewind();

        //receive response
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
        if (responseConnectBuffer.remaining() < CONNECT_RESPONSE_BUFFER_SIZE) {
            throw new TrackerException("Connect Request: Received buffer is smaller than 16 bytes");
        }
        //check action
        if (responseConnectBuffer.getInt() != ACTION_CONNECT ) {
            throw new TrackerException("Connect Request: Received different action other than 0");
        }
        //check transactionId
        if (responseConnectBuffer.getInt() != connectTransactionId) {
            throw new TrackerException("Connect Request: Received different transactionId");
        }
        // acquire connectionId
        long connectionId = responseConnectBuffer.getLong();

        //prepare packet for announce request
        announceBuffer.order(ByteOrder.BIG_ENDIAN);

        announceBuffer.putLong(connectionId);
        announceBuffer.putInt(ACTION_ANNOUNCE); //action = announce = 1
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
        announceBuffer.putInt(DEFAULT_NUMWANT); //numWant, default to -1
        announceBuffer.putShort((short) port); //TODO need to have an instance variable for this in UDPManager

        //sending announce request
        announceBuffer.flip();
        try {
            channel.send(announceBuffer, address);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //receive response
        responseAnnounceBuffer.clear();
        try {
            channel.receive(responseAnnounceBuffer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //parse response
        if (responseAnnounceBuffer.remaining() < MINIMUM_ANNOUNCE_RESPONSE_BUFFER_SIZE) {
            throw new TrackerException("Announce Request: Received buffer is smaller than 20 bytes");
        }
        //check action
        if (responseAnnounceBuffer.getInt() != ACTION_ANNOUNCE) {
            throw new TrackerException("Announce Request: Received different action other than 1");
        }
        //check transactionId
        if (responseAnnounceBuffer.getInt() != announceTransactionId) {
            throw new TrackerException("Announce Request: Received different transactionId");
        }
        int interval = responseAnnounceBuffer.getInt(); //TODO assign to instance variable instead - PEER_SIZE = 6
        int leechers = responseAnnounceBuffer.getInt();
        int seeders = responseAnnounceBuffer.getInt();
        extractPeers(responseAnnounceBuffer); //get data and store in 'peers'
        System.out.println("Interval: " + interval);
        System.out.println("Leechers: " + leechers);
        System.out.println("Seeders: " + seeders);
        System.out.println("Peers: " +peers);
    }

    private void extractPeers(ByteBuffer responseAnnounceBuffer) {
        int remainingBytes = responseAnnounceBuffer.remaining();
        if (remainingBytes % PEER_LENGTH != 0) {
            throw new TrackerException("Announce Request: Peer data length is not multiple of 6 bytes");
        }
        int numPeers = remainingBytes / PEER_LENGTH;

        // Parse peers
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

    public void announce() {
    }
}
