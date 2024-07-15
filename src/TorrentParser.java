import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class TorrentParser {
    private String announce = null;
    private String[][] annouceList = null;
    private String creationDate = null;
    private String comment = null;
    private String createdBy = null;
    private String encoding = null;
    private long pieceLength = 0;
    private byte[] pieces = null;
    private boolean isPrivate = false;
    private String name = null;
    private long length = 0;
    private List<FileInfo> files = null;
    private boolean isSingleFile = true;
    private TorrentMetaData torrentMetaData = null;
    String getString(FileInputStream fis, int currentPointer) throws IOException {
        StringBuilder stringLength = new StringBuilder();
        stringLength.append((char) currentPointer);
        int d;
        // get the length of the string
        // when not reaching ':' = 58 yet
        while ((d = fis.read()) != 58) {
            stringLength.append((char) d);
        }
        int length = Integer.parseInt(stringLength.toString());

        // read lengthKey amount of string from fis
        byte[] lengthKey = new byte[length];
        fis.read(lengthKey);
        return new String(lengthKey);
    }

    String getInteger(FileInputStream fis) throws IOException {
        int d;
        StringBuilder integer = new StringBuilder();
        //when still not reaching 'e' = 101 yet
        while ((d = fis.read()) != 101) {
            integer.append((char) d);
        }
        return integer.toString();
    }

    String getList(FileInputStream fis) throws IOException {
        StringBuilder list = new StringBuilder();
        int d;
        //when still not reaching 'e' = 101 yet
        while ((d = fis.read()) != 101) {
            String v = matchBencodedType(fis, d);
            list.append(v);
            list.append(' ');
        }
//        list.deleteCharAt(list.length() - 1);
        return list.toString();
    }

    String getDictionary(FileInputStream fis) throws IOException {
        StringBuilder list = new StringBuilder();
        int d;
        //when still not reaching 'e' = 101 yet
        while ((d = fis.read()) != 101) {
            String key = matchBencodedType(fis, d);
            int v = fis.read();
            String value = matchBencodedType(fis, v);
            matchTorrentInfoType(key, value);
            list.append(key).append("$$$").append(value).append("%%%");
        }
        list.deleteCharAt(list.length() - 1);
        list.deleteCharAt(list.length() - 1);
        list.deleteCharAt(list.length() - 1);

        return list.toString();
    }


    String matchBencodedType(FileInputStream fis, int currentPointer) throws IOException {
        return switch (currentPointer) {
            //case = 'i' = 105
            case 105 -> getInteger(fis);
            //case = 'L' = 108
            case 108 -> getList(fis);
            //case = 'd' = 100
            case 100 -> getDictionary(fis);
            default -> getString(fis, currentPointer);
        };
    }

    void matchTorrentInfoType(String key, String value) {
        switch (key) {
            case "announce":
                this.announce = value;
                break;
            case "announce-list": {
                String[] groups = value.split("  +");  // Split on two or more spaces

                String[][] result = new String[groups.length][];
                for (int i = 0; i < groups.length; i++) {
                    result[i] = groups[i].split(" ");  // Split each group on a single space
                }
                this.annouceList = result;
                break;
            }
            case "creation date":
                this.creationDate = value;
                break;
            case "comment":
                this.comment = value;
                break;
            case "created by":
                this.createdBy = value;
                break;

            case "encoding":
                this.encoding = value;
                break;

            case "piece length":
                this.pieceLength = Long.parseLong(value);
                break;

            case "pieces":
                this.pieces = value.getBytes();
                break;

            case "private":
                this.isPrivate = value.equals("1");
                break;

            case "name":
                this.name = value;
                break;

            case "length":
                this.length = this.isSingleFile ? Long.parseLong(value) : 0;
                break;

            case "files": {
                this.isSingleFile = false;
                var files = value.split("  +");
                List<FileInfo> fileInfos = new LinkedList<>();
                for (var file : files) {
                    var contents = file.split("%%%");
                    long length = 0;
                    String path = "";
                    for (var content : contents) {
                        var attributes = content.split("\\$\\$\\$");
                        switch (attributes[0]) {
                            case "length":
                                length = Long.parseLong(attributes[1]);
                            case "path":
                                path = attributes[1];
                        }
                    }
                    FileInfo fileTorrent = new FileInfo(length,path);
                    fileInfos.add(fileTorrent);
                }
                this.files = fileInfos;
            };
            default:
//            case "md5sum": this.md5sum = value;

        }
    }

    public TorrentMetaData parse(String file) {
        try (FileInputStream fis = new FileInputStream(new File(file));) {
            int d;
            while ((d = fis.read()) != -1) {
                 matchBencodedType(fis, d);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<FileInfo> fileInfos = files;
        Info info = new Info(pieceLength,pieces, isPrivate,name,length, fileInfos);
        return new TorrentMetaData(info,announce,annouceList,creationDate,comment,createdBy,encoding);

    }

    public static void main(String[] args) {}
}
