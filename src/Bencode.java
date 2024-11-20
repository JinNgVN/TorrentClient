import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

public class Bencode {

    private static Object decode(byte[] data, Pointer p) {
        return switch (data[p.value]) {
            case 'i' -> decodeLong(data, p);
            case 'l' -> decodeList(data, p);
            case 'd' -> decodeMap(data, p);
            default -> decodeString(data, p);
        };
    }

    private static String decodeString(byte[] data, Pointer p) {
        StringBuilder builder = new StringBuilder();
        while (data[p.value] != ':') {
            builder.append((char) data[p.value]);
            p.value++;
        }
        p.value++; // skip ':'

        int length = Integer.parseInt(builder.toString());
        String result = new String(data, p.value, length);
        p.value += length;
        return result;
    }

    private static Long decodeLong(byte[] data, Pointer p) {
        p.value++; // skip 'i'
        StringBuilder builder = new StringBuilder();
        while (data[p.value] != 'e') {
            builder.append((char) data[p.value]);
            p.value++;
        }
        p.value++; // skip 'e'
        return Long.parseLong(builder.toString());
    }

    private static List<Object> decodeList(byte[] data, Pointer p) {
        p.value++; // skip 'l'
        List<Object> list = new ArrayList<>();
        while (data[p.value] != 'e') {
            list.add(decode(data, p));
        }
        p.value++; // skip 'e'
        return list;
    }

    private static Map<String, Object> decodeMap(byte[] data, Pointer p) {
        p.value++; // skip 'd'
        Map<String, Object> map = new HashMap<>();
        while (data[p.value] != 'e') {
            String key = (String) decode(data, p);
            Object value = decode(data, p);
            map.put(key, value);
        }
        p.value++; // skip 'e'
        return map;
    }


    public static Object decode(byte[] data) {
        return decode(data, new Pointer(0));
    }

    public static TorrentMetaData parse(String path) {
        String announce = null;
        List<List<String>> announceList = null;
        Long creationDate = null;
        String comment = null;
        String createdBy = null;
        String encoding = null;
        TorrentMetaData.Info info = null;
        byte[] infoHash = null;

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(path))) {
            var data = bis.readAllBytes();
            var decodedData = (Map<?, ?>) decode(data);
            //get some metadata
            announce = (String) decodedData.get("announce");
            announceList = (List<List<String>>) decodedData.get("announce-list");
            creationDate = (Long) decodedData.get("creation date");
            comment = (String) decodedData.get("comment");
            createdBy = (String) decodedData.get("created by");
            encoding = (String) decodedData.get("encoding");

            //get info
            TorrentMetaData.Info.SingleModeInfo singleModeInfo = null;
            TorrentMetaData.Info.MultiModeInfo multiModeInfo = null;
            var infoDictionary = (Map<?, ?>) decodedData.get("info");

            //check if single file mode or multiple file mode
            if (infoDictionary.get("files") == null) {
                //single file mode
                singleModeInfo = new TorrentMetaData.Info.SingleModeInfo(
                        (String) infoDictionary.get("name"),
                        (long) infoDictionary.get("length")
                );
            } else {
                //multiple file mode
                var listFile = (List<Map<?, ?>>) infoDictionary.get("files");
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
                    infoDictionary.get("private") != null, //TODO have to check the value of private if set
                    singleModeInfo,
                    multiModeInfo
            );

            //get infoHash
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            infoHash = sha1.digest(encode(infoDictionary));

        } catch (IOException e) {
            System.out.println(e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return new TorrentMetaData(announce, announceList, creationDate, comment, createdBy, encoding, info, infoHash);
    }

    public static byte[] encode(Object data) {
        return switch (data) {
            case String s -> encodeString(s);
            case Number n -> encodeInteger(n.longValue());
            case List<?> list -> encodeList(list);
            case Map<?, ?> map -> encodeMap(map);
            default -> throw new BencodeException("Wrong torrent format!");
        };
    }

    private static byte[] encodeString(String s) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            output.write(String.valueOf(s.length()).getBytes());
            output.write(':');
            output.write(s.getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        } catch (IOException e) {
            throw new BencodeException("Failed to encode string", e);
        }
    }

    private static byte[] encodeInteger(long l) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            output.write('i');
            output.write(String.valueOf(l).getBytes());
            output.write('e');
            return output.toByteArray();
        } catch (IOException e) {
            throw new BencodeException("Failed to encode integer", e);
        }
    }

    private static byte[] encodeList(List<?> list) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            output.write('l');
            for (var item : list) {
                output.write(encode(item));
            }
            output.write('e');
            return output.toByteArray();

        } catch (IOException e) {
            throw new BencodeException("Failed to encode list", e);
        }
    }

    private static byte[] encodeMap(Map<?, ?> map) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            output.write('d');

            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(entry.getKey().toString(), entry.getValue());
            }

            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                output.write(encode(entry.getKey()));
                output.write(encode(entry.getValue()));
            }
            output.write('e');
            return output.toByteArray();
        } catch (IOException e) {
            throw new BencodeException("Failed to encode map", e);
        }
    }

    public static void main(String[] args) {
        var data = Bencode.parse("./src/file.torrent");
        System.out.println(Arrays.toString(data.infoHash()));
        ;

        //        System.out.println(data);}
//        Map<String, Object> map = new HashMap<>();
//        map.put("Hello", 5L);
//        map.put("World", 10L);
//        map.put("Map", List.of(1,2,3,4,"world"));
//        System.out.println(new String(Bencode.encode(map)));

    }
}
