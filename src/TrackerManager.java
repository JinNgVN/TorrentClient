import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;

public class TrackerManager {
    private final List<TrackerConnection> trackerConnections;
    private static final String CLIENT_ID = "JB";
    private static final String VERSION = "0001";
    private static final SecureRandom RANDOM = new SecureRandom();

    public TrackerManager(String torrentPath) {
        // Parse torrent file
        var torrentData = Bencode.parse(torrentPath);

        // Generate peer ID
        byte[] peerId = generate();

        // Collect announce URLs
        List<String> announceUrls = new ArrayList<>();
        announceUrls.add(torrentData.announce());

        // Handle announce-list (list of lists)
        if (torrentData.announceList() != null) {
            torrentData.announceList().stream()
                    .flatMap(List::stream)  // Flatten list of lists
                    .forEach(announceUrls::add);
        }
        // Create tracker connections
        this.trackerConnections = announceUrls.stream()
                .map(url -> new TrackerConnection(
                        url,
                        peerId,
                        torrentData.infoHash()
                ))
                .toList();  // Creates immutable list
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

    public void start() {
        // Start all tracker connections
        trackerConnections.forEach(TrackerConnection::sendRequest);
    }

    public static void main(String[] args) {
        TrackerManager manager = new TrackerManager("./src/file.torrent");
        manager.start();
    }
}