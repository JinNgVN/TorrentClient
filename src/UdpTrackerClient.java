import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class UdpTrackerClient {
    private final byte[] peerId;
    private final String announceUrl;
    private final byte[] infoHash;
    private List<Peer> peers = new ArrayList<>();
    private long uploaded = 0;
    private long downloaded = 0;
    private long left;


    private static final int port = 6881;

    private static final Random RANDOM = new Random();
    private final int key = RANDOM.nextInt();

    private int transactionId;

    //constant
    //connect request
    private static final int CONNECT_REQUEST_BUFFER_SIZE = 16;
    private final ByteBuffer connectRequestBuffer = ByteBuffer.allocate(CONNECT_REQUEST_BUFFER_SIZE);

    //connect response
    private static final int CONNECT_RESPONSE_BUFFER_SIZE = 16;
    //announce request
    private static final int ANNOUNCE_REQUEST_BUFFER_SIZE = 98;
    private final ByteBuffer announceRequestBuffer = ByteBuffer.allocate(ANNOUNCE_REQUEST_BUFFER_SIZE);
    //receive buffer
    private final ByteBuffer receiveBuffer = ByteBuffer.allocate(INITIAL_ANNOUNCE_RESPONSE_BUFFER_SIZE);
    private static final int ACTION_CONNECT = 0;
    private static final int ACTION_ANNOUNCE = 1;
    private static final int ACTION_SCRAPE = 2;
    private static final int ACTION_ERROR = 3;

    //announce response
    private static final int INITIAL_ANNOUNCE_RESPONSE_BUFFER_SIZE = 1024;
    private static final int MINIMUM_ANNOUNCE_RESPONSE_BUFFER_SIZE = 20;

    //error response
    private static final int MINIMUM_ERROR_MESSAGE = 8;

    private static final long MAGIC_CONSTANT = 0x41727101980L;
    private static final int DEFAULT_NUMWANT = -1;
    private static final int PEER_LENGTH = 6;


    private final DatagramChannel channel;


    public UdpTrackerClient(String announceUrl, byte[] peerId, byte[] infoHash, long size) throws IOException, UnresolvedAddressException {
        this.announceUrl = announceUrl;
        this.peerId = peerId;
        this.infoHash = infoHash;
        this.left = size;

        //configure the channel
        channel = DatagramChannel.open();
        channel.bind(null);
        channel.configureBlocking(false);
        channel.connect(parseUdpAnnounceUrl(announceUrl));

        //register with selector
        SelectorManager selectorManager = SelectorManager.getInstance();
        selectorManager.registerConnection(channel, this);

        //set order
        connectRequestBuffer.order(ByteOrder.BIG_ENDIAN);
        announceRequestBuffer.order(ByteOrder.BIG_ENDIAN);
        receiveBuffer.order(ByteOrder.BIG_ENDIAN);
    }


    public void startAnnouncing() throws IOException {
        sendConnectRequest();
    }

    public void handleResponse() throws IOException {
        receiveBuffer.clear();
        channel.receive(receiveBuffer);
        receiveBuffer.flip();

        int size = receiveBuffer.remaining();
        int action = receiveBuffer.getInt();
        int transactionId = receiveBuffer.getInt();


        switch (action) {
            case ACTION_CONNECT -> {
                System.out.println("Received CONNECT request");
                if (size < CONNECT_RESPONSE_BUFFER_SIZE) {
                    throw new TrackerException("Connect Request: Received buffer is smaller than 16 bytes");
                }
                //check transactionId
                if (transactionId != this.transactionId) {
                    throw new TrackerException("Connect Request: Received different transactionId");
                }
                //get connectionId
                long connectionId = receiveBuffer.getLong();
                sendAnnounceRequest(connectionId, TrackerEvent.STARTED); //TODO have to change the event dynamically
            }
            case ACTION_ANNOUNCE -> {
                System.out.println("Received announce response");
                if (size < MINIMUM_ANNOUNCE_RESPONSE_BUFFER_SIZE) {
                    throw new TrackerException("Announce Request: Received buffer is smaller than 20 bytes");
                }
                //check transactionId
                if (transactionId != this.transactionId) {
                    throw new TrackerException("Announce Request: Received different transactionId");
                }
                int interval = receiveBuffer.getInt(); //TODO assign to instance variable instead - PEER_SIZE = 6
                int leechers = receiveBuffer.getInt();
                int seeders = receiveBuffer.getInt();
                extractPeers(receiveBuffer); //get data and store in 'peers'
                System.out.println("Interval: " + interval);
                System.out.println("Leechers: " + leechers);
                System.out.println("Seeders: " + seeders);
                System.out.println("Peers: " +peers);
            }
            case ACTION_SCRAPE -> {

            }
            case ACTION_ERROR -> {
                if (size < MINIMUM_ERROR_MESSAGE) {
                    throw new TrackerException("Error message: Received buffer is smaller than 8 bytes");
                }
                //check transactionId
                if (transactionId != this.transactionId) {
                    throw new TrackerException("Error message: Received different transactionId");
                }
                byte[] messageBytes = new byte[receiveBuffer.remaining()];
                receiveBuffer.get(messageBytes);
                String errorMessage = new String(messageBytes, StandardCharsets.UTF_8);
                System.out.println("Receive Error: " + errorMessage);
            }

        }

    }

    private void sendConnectRequest() throws IOException {
        //build connect request packet
        connectRequestBuffer.clear();
        connectRequestBuffer.putLong(MAGIC_CONSTANT); //magic constant
        connectRequestBuffer.putInt(ACTION_CONNECT); // action = connect = 0
        transactionId = RANDOM.nextInt();
        connectRequestBuffer.putInt(transactionId);

        //sending connect request
        connectRequestBuffer.flip();
        channel.write(connectRequestBuffer);
    }

    private void sendAnnounceRequest(long connectionId, TrackerEvent event) throws IOException {
        //build announce request packet
        announceRequestBuffer.clear();
        announceRequestBuffer.putLong(connectionId);
        announceRequestBuffer.putInt(ACTION_ANNOUNCE); //action = announce = 1
        transactionId = RANDOM.nextInt();
        announceRequestBuffer.putInt(transactionId);
        announceRequestBuffer.put(infoHash);
        announceRequestBuffer.put(peerId);
        announceRequestBuffer.putLong(downloaded);
        announceRequestBuffer.putLong(left);
        announceRequestBuffer.putLong(uploaded);
        announceRequestBuffer.putInt(event.getIntValue());
        announceRequestBuffer.putInt(0); //TODO This is ip address, default to 0, need to study this more
        announceRequestBuffer.putInt(key); //key for all session of this torrent
        announceRequestBuffer.putInt(DEFAULT_NUMWANT); //numWant, default to -1
        announceRequestBuffer.putShort((short) port);

        //sending announce request
        announceRequestBuffer.flip();
        channel.write(announceRequestBuffer);
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

}
