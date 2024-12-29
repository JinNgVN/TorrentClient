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
            case '1', '2', '3', '4', '5', '6', '7', '8', '9', '0' -> decodeString(data, p);
            default -> throw new IllegalArgumentException("Invalid Bencode");
        };
    }

    private static String decodeString(byte[] data, Pointer p) {
        StringBuilder builder = new StringBuilder();
        while (data[p.value] != ':') {
            if (!Character.isDigit(data[p.value])) {
                throw new IllegalArgumentException("Invalid length prefix");
            }
            builder.append((char) data[p.value]);
            p.value++;
        }
        p.value++; // skip ':'

        int length = Integer.parseInt(builder.toString());
        if (p.value + length > data.length) {
            throw new IllegalArgumentException("String length exceeds data bound");
        }
        String result = new String(data, p.value, length, StandardCharsets.ISO_8859_1);
        p.value += length;
        return result;
    }

    private static Long decodeLong(byte[] data, Pointer p) {
        p.value++; // skip 'i'
        StringBuilder builder = new StringBuilder();
        while (data[p.value] != 'e') {
            if (!Character.isDigit(data[p.value])) {
                throw new IllegalArgumentException("Invalid bencoded number");
            }
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
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(path))) {
            var data = bis.readAllBytes();

            var decodedData = (Map<?, ?>) decode(data);


            //get info
            TorrentMetaData.SingleModeInfo singleModeInfo = null;
            TorrentMetaData.MultiModeInfo multiModeInfo = null;
            var infoDictionary = (Map<?, ?>) decodedData.get("info");

            //check if single file mode or multiple file mode
            if (infoDictionary.get("files") == null) {
                //single file mode
                singleModeInfo = new TorrentMetaData.SingleModeInfo(
                        (String) infoDictionary.get("name"),
                        (long) infoDictionary.get("length")
                );
            } else {
                //multiple file mode
                var listFile = (List<Map<?, ?>>) infoDictionary.get("files");
                var files = listFile.stream()
                        .map(fileMap -> new TorrentMetaData.MultiModeInfo.File(
                                (long) fileMap.get("length"),
                                // Assuming path is a List<String> and we take the first element
                                ((List<String>) fileMap.get("path")).get(0)
                        ))
                        .collect(Collectors.toList());

                multiModeInfo = new TorrentMetaData.MultiModeInfo(
                        (String) infoDictionary.get("name"),
                        files
                );
            }


            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] infoHash = sha1.digest(encode(infoDictionary));
            //get metadata
            String announce = (String) decodedData.get("announce");
            List<List<String>> announceList = (List<List<String>>) (List<?>) decodedData.get("announce-list");
            Long creationDate = (Long) decodedData.get("creation date");
            String comment = (String) decodedData.get("comment");
            String createdBy = (String) decodedData.get("created by");
            String encoding = (String) decodedData.get("encoding");
            Long pieceLength = (Long) infoDictionary.get("piece length");
            byte[] pieces = ((String) infoDictionary.get("pieces")).getBytes();
            boolean isPrivate = false;


            return new TorrentMetaData(announce,
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
                    multiModeInfo);

        } catch (IOException | NoSuchAlgorithmException | ClassCastException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] encode(Object data) {
        return switch (data) {
            case String s -> encodeString(s);
            case Number n -> encodeInteger(n.longValue());
            case List<?> list -> encodeList(list);
            case Map<?, ?> map -> encodeMap(map);
            default ->
                    throw new IllegalArgumentException("Invalid torrent format! Only accept String, Number, List and Map");
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
        Bencode.parse("./src/file.torrent");

    }

    public static class Pointer {
        public int value;

        public Pointer(int value) {
            this.value = value;
        }
    }
}
