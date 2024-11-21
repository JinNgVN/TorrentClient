import java.io.IOException;
import java.nio.channels.DatagramChannel;

public class SingletonDatagramChannel {
    private static DatagramChannel instance;

    public static DatagramChannel getInstance() {
        try {
            if (instance == null) {
                instance = DatagramChannel.open();
                instance.configureBlocking(false);
            }
            return instance;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
