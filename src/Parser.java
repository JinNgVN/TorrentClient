import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Parser {
    private String announce = null;
    private ArrayList<String> annouceList = null;
    private String creationDate = null;
    private String comment = null;
    private String createdBy = null;
    private String encoding = null;
    private long pieceLength = -1;
    private byte[] pieces = null;
    private boolean isPrivate = false;
    private String name = null;
    private long length = -1;
    private List<FileInfo> files = null;
    private byte[] rawInfoContent = null;
    private byte[] hashInfoContent = null;
    private int infoIndex;


    public String getAnnounce() {
        return announce;
    }

    public byte[] getHashInfoContent() {
        return hashInfoContent;
    }

    private byte[] extractRawInfoContent(byte[] content) throws IOException {
        // Convert to string for easier searching
        String contentStr = new String(content, StandardCharsets.ISO_8859_1);
        // Find the index of "info:"
        int infoIndex = contentStr.indexOf("info");
        // If "info:" is found, extract everything after it
        return contentStr.substring(infoIndex + 4 ).getBytes(StandardCharsets.ISO_8859_1);

    }

    public void parse(String path) {
        Bencode bencode = new Bencode();

        try (FileInputStream fis =  new FileInputStream(new File(path))) {
            byte[] contentFile = fis.readAllBytes();

            Map<String, Object> dict = bencode.decode(contentFile, Type.DICTIONARY);
            announce = (String) dict.get("announce");
            annouceList = (ArrayList<String>) dict.get("announce-list");
            creationDate = (String) dict.get("creation date");
            comment = (String) dict.get("comment");
            createdBy = (String) dict.get("created by");
            encoding = (String) dict.get("encoding");
            var info = (Map<String, Object>) dict.get("info");
            pieceLength = (long) info.get("piece length");
            pieces = ((String) info.get("pieces")).getBytes();
            name = (String) info.get("name");
            length =  info.get("length") != null ? (long) info.get("length") : -1;

            //check if it is multiple or single file
            var files = (ArrayList<Map<String, Object>>)info.get("files");
            if (files != null) {
                List<FileInfo> fileInfoList = new ArrayList<>(files.size());
                for (var item: files) {
                    FileInfo fileInfo = new FileInfo((long) item.get("length"), (List<String>) item.get("path"));
                    fileInfoList.add(fileInfo);
                }
                this.files = fileInfoList;
            }
            rawInfoContent = extractRawInfoContent(contentFile);
            //Hash the info
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                hashInfoContent = md.digest(rawInfoContent);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-1 algorithm not found", e);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Parser p = new Parser();
        p.parse("file.torrent");
    }
}
