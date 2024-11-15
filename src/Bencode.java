import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Bencode {
    /**
     * Decodes bencoded data into Java objects.
     * @param data The bencoded byte array
     * @param pointer Current position in the byte array
     * @return Decoded object (String, Long, List, or Map)
     * @throws BencodeException if the data is malformed
     */
    //TODO implement BencodeException
    public static Object decode(byte[] data, Pointer pointer) {
        switch (data[pointer.value]) {
            //Dictionary
            case 'd' -> {
                pointer.value++;
                Map<Object, Object> map = new HashMap<>();
                while(data[pointer.value] != 'e') {
                    var key = decode(data, pointer);
                    var value = decode(data, pointer);
                    map.put(key,value);
                }
                pointer.value++; //skip 'e' at the end
                return map;
            }
            //List
            case 'l' -> {
                pointer.value++;
                List<Object> list = new ArrayList<>();
                while (data[pointer.value] != 'e') {
                    var item = decode(data, pointer);
                    list.add(item);
                }
                pointer.value++; //skip 'e' at the end
                return list;
            }
            //Integer
            case 'i' -> {
                StringBuilder builder = new StringBuilder();
                pointer.value++; //skip i at the beginning
                while (data[pointer.value] != 'e') {
                    builder.append((char) data[pointer.value]);
                    pointer.value++;
                }
                pointer.value++; //skip e at the end
                String num = builder.toString();
                var n1 = Long.parseLong(num);
                return Long.parseLong(num);
            }
            //String
            default -> {
                StringBuilder builder = new StringBuilder();
                while (data[pointer.value] != ':') {
                    builder.append((char) data[pointer.value]);
                    pointer.value++;
                }
                pointer.value++; //skip ':'
                // Read the string
                int length = Integer.parseInt(builder.toString());
                String result = new String(data, pointer.value, length);
                pointer.value += length; // Move pointer past the string
                return result;
            }
        }
    }

    public static TorrentMetaData parse(String path) {
        String announce = null;
        List<String> announceList = null;
        Long creationDate = null;
        String comment =null;
        String createdBy = null;
        String encoding = null;
        TorrentMetaData.Info info = null;

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(path))) {
            var data = bis.readAllBytes();
            var decodedData = (Map<Object , Object>) decode(data, new Pointer(0));
            //get some metadata
            announce = (String) decodedData.get("announce");
            announceList = (List<String>) decodedData.get("announce-list");
            creationDate = (Long) decodedData.get("creation date");
            comment = (String) decodedData.get("comment");
            createdBy = (String) decodedData.get("created by");
            encoding = (String) decodedData.get("encoding");

            //get info
            TorrentMetaData.Info.SingleModeInfo singleModeInfo = null;
            TorrentMetaData.Info.MultiModeInfo multiModeInfo = null;
            var infoDictionary = (Map<Object,Object>) decodedData.get("info");

            //check if single file mode or multiple file mode
            if (infoDictionary.get("files") == null ) {
                singleModeInfo = new TorrentMetaData.Info.SingleModeInfo(
                        (String) infoDictionary.get("name"),
                        (long) infoDictionary.get("length")
                );
            } else {
                var listFile = (List<Map<Object,Object>>) infoDictionary.get("files");
                var files = listFile.stream()
                        .map(fileMap -> new TorrentMetaData.Info.MultiModeInfo.File(
                                (long) fileMap.get("length"),
                                // Assuming path is a List<String> and we take the first element
                                ((List<String>) fileMap.get("path")).get(0)
                        ))
                        .collect(Collectors.toList());

                multiModeInfo = new TorrentMetaData.Info.MultiModeInfo(
                        (String) infoDictionary.get("name"),
                        files
                        );
            }
            info = new TorrentMetaData.Info(
                    (long) infoDictionary.get("piece length"),
                    ((String) infoDictionary.get("pieces")).getBytes(),
                    infoDictionary.get("private") != null, //have to check the value of private if set
                    singleModeInfo,
                    multiModeInfo
                    );

        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return new TorrentMetaData(announce, announceList, creationDate, comment, createdBy, encoding, info);
    }

    public static void main(String[] args) {
        var data = Bencode.parse("./src/SONE-448.torrent");
        System.out.println(data);
    }
}
