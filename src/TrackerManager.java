import java.io.IOException;
import java.nio.channels.Selector;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;

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

    public void getPeer() {
        // Parse torrent file
        TorrentMetaData torrentData = Bencode.parse(pathToTorrent);

        // Generate peer ID
        byte[] peerId = generate();

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
        System.out.println(announceUrls);

        for (String announceUrl : announceUrls) {
            try {
                UdpTrackerClient client = new UdpTrackerClient(announceUrl, peerId, torrentData.infoHash());
                client.announce();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
    }

    public static void main(String[] args) throws IOException {
        TrackerManager manager = new TrackerManager("./src/file.torrent");
        manager.getPeer();
    }
}