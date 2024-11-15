import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class TrackerConnection {
    private int minInterval;
    private int interval;
    private int uploaded = 0;
    private int downloaded = 0;
    private int left;
    private String TrackerId;
    private int numWant;
    private  TrackerEvent event;


    private String peerId;
    private String baseUrl;
    private String infoHash;

    private  Status status;


    public TrackerConnection(String baseUrl, String peerId, String infoHash) {
        this.baseUrl = baseUrl;
        this.peerId = peerId;
        this.infoHash = infoHash;
    }

    public void sendRequest() {
        if (baseUrl.startsWith("udp://")) {
            sendUdpRequest();
        } else if (baseUrl.startsWith("http://") | baseUrl.startsWith("https://")) {
            sendHttpRequest();
        } else if (baseUrl.startsWith("wss://") | baseUrl.startsWith("ws://")) {
            sendWsRequest();

        }
        //TODO add a new throw for wrong protocol besides udp, http, https, ws or wss
//        else {
//            throw errror
//        }

    }

    private void sendHttpRequest() {
        try (var httpClient = HttpClient.newBuilder()
                // Protocol version (HTTP 1.1 is more widely supported for trackers)
                .version(HttpClient.Version.HTTP_1_1)
                // Timeouts
                .connectTimeout(Duration.ofSeconds(15))
                // Redirects
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {

            var request = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(baseUrl))
                    .header("User-Agent", "BitTorrent/7001")
                    .header("Accept-Encoding", "gzip")
                    .header("Connection", "close")
                    .build();

            var response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body);
        }

    }

    private void sendUdpRequest() {


    }

    private void sendWsRequest() {

    }
}
