import java.util.List;

public record TorrentMetaData(Info info,
                              String announce,
                              String[][] announceList,
                              String creationDate,
                              String comment,
                              String createdBy,
                              String encoding) {
}

record Info(long pieceLength,
            byte[] pieces,
            boolean isPrivate,
            String name,
            long length,
//            String md5sum,
            List<FileInfo> file) {

}

record FileInfo(long length, List<String> path) {

}
