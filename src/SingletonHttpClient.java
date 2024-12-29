import java.net.http.HttpClient;
import java.time.Duration;

public class SingletonHttpClient {
    private static HttpClient client;
    public SingletonHttpClient() {

    }
    public static HttpClient getClient() {
        if (client == null) {
            client = HttpClient.newBuilder()
                    // Protocol version (HTTP 1.1 is more widely supported for trackers)
                    .version(HttpClient.Version.HTTP_1_1)
                    // Timeouts
                    .connectTimeout(Duration.ofSeconds(15))
                    // Redirects
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return client;
    }
}
