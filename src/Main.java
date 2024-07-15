public class Main {
    public static void main(String[] args) {
        TorrentParser parser = new TorrentParser();
        String file = "SSIS-088.torrent";
        var metadata = parser.parse(file);
        System.out.println(metadata);

    }
}