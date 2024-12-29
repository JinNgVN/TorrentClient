import java.util.List;

public record TorrentMetaData(String announce,
                              List<List<String>> announceList,
                              Long creationDate,
                              String comment,
                              String createdBy,
                              String encoding,
                              byte[] infoHash,
                              long pieceLength,
                              byte[] pieces,
                              boolean isPrivate,
                              SingleModeInfo singleModeInfo,
                              MultiModeInfo multiModeInfo,
                              long size) {



    public TorrentMetaData(String announce,
                           List<List<String>> announceList,
                           Long creationDate,
                           String comment,
                           String createdBy,
                           String encoding,
                           byte[] infoHash,
                           long pieceLength,
                           byte[] pieces,
                           boolean isPrivate,
                           SingleModeInfo singleModeInfo,
                           MultiModeInfo multiModeInfo) {
        this(announce,
                announceList,
                creationDate,
                comment,
                createdBy,
                encoding,
                infoHash,
                pieceLength,
                pieces,
                isPrivate,
                singleModeInfo,
                multiModeInfo,
                calculateTotalSize(singleModeInfo, multiModeInfo));
    }

    private static long calculateTotalSize(SingleModeInfo singleModeInfo, MultiModeInfo multiModeInfo) {
        if (singleModeInfo != null) {
            return singleModeInfo.length();
        } else if (multiModeInfo != null) {
            return multiModeInfo.files().stream()
                    .mapToLong(TorrentMetaData.MultiModeInfo.File::length)
                    .sum();
        }
        return 0;
    }

    public record SingleModeInfo(String name, long length) {
    }

    public record MultiModeInfo(String name, List<File> files) {
        public record File(long length, String path) {
        }
    }
}
