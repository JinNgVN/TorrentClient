import java.util.List;
import java.io.File;
public record TorrentMetaData(String announce,
                              List<List<String>> announceList,
                              Long creationDate,
                              String comment,
                              String createdBy,
                              String encoding,
                              Info info,
                              byte[] infoHash,
                              long size) {

    public TorrentMetaData(String announce,
                           List<List<String>> announceList,
                           Long creationDate,
                           String comment,
                           String createdBy,
                           String encoding,
                           Info info,
                           byte[] infoHash) {
        this(announce, announceList, creationDate, comment, createdBy,
                encoding, info, infoHash, calculateTotalSize(info));
    }

    private static long calculateTotalSize(Info info) {
        if (info.singleModeInfo() != null) {
            return info.singleModeInfo().length();
        } else if (info.multiModeInfo() != null) {
            return info.multiModeInfo().files().stream()
                    .mapToLong(Info.MultiModeInfo.File::length)
                    .sum();
        }
        return 0;
    }

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