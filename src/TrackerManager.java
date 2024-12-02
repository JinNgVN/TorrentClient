import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.UnresolvedAddressException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class TrackerManager {
    private static final String CLIENT_ID = "JB";
    private static final String VERSION = "0001";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final String pathToTorrent;

    public TrackerManager(String pathToTorrent) throws IOException {
        this.pathToTorrent = pathToTorrent;
    }

    private static byte[] generate() {
        StringBuilder peerId = new StringBuilder(20);
        peerId.append('-')
                .append(CLIENT_ID)
                .append(VERSION)
                .append('-');

        for (int i = 0; i < 11; i++) {
            peerId.append((char) (RANDOM.nextInt(26) + 'a'));
        }

        return peerId.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] generatePeerId(String clientId, String version) {
        // Using Azureus-style: -XX1234-[random letters]
        StringBuilder peerId = new StringBuilder(20);

        // Example using 'AZ' for Azureus: -AZ2060-
        peerId.append('-');           // Leading dash
        peerId.append("AZ");         // Azureus client identifier
        peerId.append("2060");       // Version 2.6.0
        peerId.append('-');          // Trailing dash

        // Fill remaining 12 bytes with random characters
        Random random = new Random();
        for (int i = 0; i < 12; i++) {
            peerId.append((char)(random.nextInt(26) + 'a'));
        }

        return peerId.toString().getBytes();
    }

    public void getPeer() {
        // Parse torrent file
        TorrentMetaData torrentData = Bencode.parse(pathToTorrent);

        // Generate peer ID
        byte[] peerId = generatePeerId("MT", "1.2.3");

        // Collect announce URLs
        List<String> announceUrls = new ArrayList<>();
        announceUrls.add(torrentData.announce());

        // Handle announce-list (list of lists)
        if (torrentData.announceList() != null) {
            torrentData.announceList().stream()
                    .flatMap(List::stream)  // Flatten list of lists
                    .filter(url -> url.startsWith("udp://"))
                    .forEach(announceUrls::add);
        }
        announceUrls.forEach(System.out::println);

        for (String announceUrl : announceUrls) {
            try {
                UdpTrackerClient client = new UdpTrackerClient(announceUrl, peerId, torrentData.infoHash(), torrentData.size());
                client.startAnnouncing();
            } catch (IOException | UnresolvedAddressException e) {
                System.out.println("Failed to announce: " + announceUrl + ": " + e.getMessage());
            }

        }
    }

    public static void main(String[] args) throws IOException {
        TrackerManager manager = new TrackerManager("./src/file.torrent");
        manager.getPeer();
    }
}