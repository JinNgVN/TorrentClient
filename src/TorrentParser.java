import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * This class is to parse the torrent file.
 */
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
    //TODO: Think: do we need to make a setter for torrentMetadata or can just return it after calling parse()
    private TorrentMetaData torrentMetaData = null;

    /**
     * Get a bencoded string (4:samp === "samp") from the current pointer of fis.
     * @param fis FileInputStream for reading torrent file
     * @param currentPointer The current pointer
     * @return The string of t
     * @throws IOException Throw error when something is wrong with the IO
     * @author Ho Dac Dang Nguyen
     */
    private String getString(FileInputStream fis, int currentPointer) throws IOException {
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

    /**
     * Get a bencoded integer (i43e = 43) from the current pointer of fis.
     * @param fis FileInputStream for reading torrent file
     * @return The string representation of the integer
     * @throws IOException Throw error when something is wrong with the IO
     * @author Ho Dac Dang Nguyen
     */
    private String getInteger(FileInputStream fis) throws IOException {
        int d;
        StringBuilder integer = new StringBuilder();
        //when still not reaching 'e' = 101 yet
        while ((d = fis.read()) != 101) {
            integer.append((char) d);
        }
        return integer.toString();
    }

    /**
     * Recursively get all the items in the bencoded list. (L4:samp5:beard3:seae === [samp,beard,sea])
     * @param fis FileInputStream for reading torrent file
     * @return The string representation of the list. Noted: between elements in the list has a space for later splitting.
     * There is also a space at the end of the string.
     * @throws IOException Throw error when something is wrong with the IO
     * @author Ho Dac Dang Nguyen
     */
    private String getList(FileInputStream fis) throws IOException {
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

    /**
     * Recursively get all the key-value pairs of the bencoded dictionary.
     * @param fis FileInputStream for reading torrent file
     * @return The string representation of the dictionary. There is "$$$" between key and value and
     * "%%%" between each pair for later splitting.
     * @throws IOException Throw error when something is wrong with the IO
     * @author Ho Dac Dang Nguyen
     */
    private String getDictionary(FileInputStream fis) throws IOException {
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

        //delete excessive %%% at the end of the string
        list.deleteCharAt(list.length() - 1);
        list.deleteCharAt(list.length() - 1);
        list.deleteCharAt(list.length() - 1);

        return list.toString();
    }


    /**
     * Determine the correct bencoded type and process it.
     * @param fis FileInputStream for reading torrent file
     * @param currentPointer The curent pointer of the file
     * @return String representation of that bencoded type
     * @throws IOException Throw error when something is wrong with the IO
     * @author Ho Dac Dang Nguyen
     * */
    private String matchBencodedType(FileInputStream fis, int currentPointer) throws IOException {
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

    /**
     * Assign values to variables to create a record that holds information of that torrent file.
     * Since the torrent is a bencoded dictionary, we need to pass in keys and values.
     * @param key The key
     * @param value The value associated with the key
     * @author Ho Dac Dang Nguyen
     */
    private void matchTorrentInfoType(String key, String value) {
        switch (key) {
            case "announce":
                this.announce = value;
                break;
            case "announce-list": {
                String[] groups = value.split("  +");  // Split on two or more spaces.

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
                this.isPrivate = value.equals("1") ? true : value.equals("0") ? false: null;
                break;
            case "name":
                this.name = value;
                break;
            case "length":
                this.length = this.isSingleFile ? Long.parseLong(value) : 0;
                break;
            case "files": {
                //When having files keyword in torrent file, it means this torrent
                //has multiple files
                this.isSingleFile = false;

                //split between each dictionary of the list
                var files = value.split("  +");
                List<FileInfo> fileInfos = new LinkedList<>();
                for (var file : files) {
                    //split between each pair of the dictionary
                    var contents = file.split("%%%");
                    long length = 0;
                    String path = "";
                    for (var content : contents) {
                        //split between key and value
                        var attributes = content.split("\\$\\$\\$");
                        switch (attributes[0]) {
                            case "length":
                                length = Long.parseLong(attributes[1]);
                            case "path":
                                path = attributes[1];
                            //TODO: Add md5sum
                        }
                    }
                    FileInfo fileTorrent = new FileInfo(length,path);
                    fileInfos.add(fileTorrent);
                }
                this.files = fileInfos;
            };
            default:
                //TODO: Add this field as well
//            case "md5sum": this.md5sum = value;
        }
    }

    /**
     * Starting to parse the torrent file.
     * @param file The directory to the file
     * @return The record that holds all the necessary information of the torrent file
     * @author Ho Dac Dang Nguyen
     */
    public TorrentMetaData decode(String file) {
        try (FileInputStream fis = new FileInputStream(new File(file));) {
            int d;
            FileOutputStream fos = new FileOutputStream(new File(file));

            while ((d = fis.read()) != -1) {
                //ignore returned value
                matchBencodedType(fis, d);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<FileInfo> fileInfos = files;
        Info info = new Info(pieceLength,pieces, isPrivate,name,length, fileInfos);
        return new TorrentMetaData(info,announce,annouceList,creationDate,comment,createdBy,encoding);
    }

    public static void main(String[] args) {
        String file = "file.torrent";
        try (FileInputStream fis = new FileInputStream(new File(file))) {
            byte[] content = fis.readAllBytes();
            Bencode bencode = new Bencode();
            var dict = bencode.decode(content, Type.DICTIONARY);
            System.out.println(dict.get("info"));

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
