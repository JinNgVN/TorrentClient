import java.util.List;

public record TorrentMetaData(String primaryTrackerUrl, List<String> trackerUrlList, String comment, String createdBy, String creationDate, String encoding) {
}
