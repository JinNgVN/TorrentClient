public class BencodeException extends RuntimeException {
    BencodeException(String message) {
        super(message);
    }
    BencodeException(String message, Throwable cause) {
        super(message, cause);
    }
}