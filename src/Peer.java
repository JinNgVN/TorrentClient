public class Peer {
    private String ip;
    private int port;
    public Peer(String ip, int port) {
        this.ip = ip;
        this.port = port;

    }

    @Override
    public String toString() {
        return "Peer [ip=" + ip + ", port=" + port + "]";
    }
}