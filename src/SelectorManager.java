import java.io.IOException;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SelectorManager {
    private static SelectorManager instance;
    private final Selector selector;
    private final Map<SelectableChannel, UdpTrackerClient> connections;

    private SelectorManager() throws IOException {
        selector = Selector.open();
        connections = new ConcurrentHashMap<>();
        startSelectorLoop();
    }

    public static synchronized SelectorManager getInstance() throws IOException {
        if (instance == null) {
            instance = new SelectorManager();
        }
        return instance;
    }

    public void registerConnection(DatagramChannel channel, UdpTrackerClient connection) throws ClosedChannelException {
        channel.register(selector, SelectionKey.OP_READ);
        connections.put(channel, connection);
        selector.wakeup();
    }

    private void startSelectorLoop() {
        Thread selectorThread = new Thread(() -> {
            try {
                while (true) {
                    int readyChannels = selector.select(1000);
                    if (readyChannels == 0) continue;
                    Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
                    while (selectedKeys.hasNext()) {
                        SelectionKey key = selectedKeys.next();

                        if (key.isReadable()) {
                            DatagramChannel channel = (DatagramChannel) key.channel();
                            UdpTrackerClient client = connections.get(channel);
                            if (client != null) {
                                client.handleResponse();
                            }
                        }

                        selectedKeys.remove();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
//        selectorThread.setDaemon(true); //ensure this thread doesn't stop JVM to shut down
        selectorThread.start();
    }


}
