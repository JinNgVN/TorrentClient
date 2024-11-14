import java.util.List;

public record TorrentMetaData(String announce,
                              List<String> announceList,
                              Long creationDate,
                              String comment,
                              String createdBy,
                              String encoding,
                              Info info) {
    public record Info(long pieceLength,
                       byte[] pieces,
                       boolean isPrivate,
                       SingleModeInfo singleModeInfo,
                       MultiModeInfo multiModeInfo) {

        public record SingleModeInfo(String name, long length) {
        }

        public record MultiModeInfo(String name, List<File> files) {
            public record File(long length, String path) {
            }
        }
    }
}
