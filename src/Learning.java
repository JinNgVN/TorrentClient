import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class Learning {
    private static final int MAX_RETRIES = 3;
    private static final long TIMEOUT = 1000; // 1 second timeout

    public static void main(String[] args) throws IOException {
        System.out.println(InetAddress.getLocalHost());
        ByteBuffer buffer = ByteBuffer.allocateDirect();
    }
}