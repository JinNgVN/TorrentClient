public class TrackerException extends RuntimeException{
    TrackerException(String message){super(message);}
    TrackerException(String message, Throwable cause) {
        super(message, cause);
    }
}
