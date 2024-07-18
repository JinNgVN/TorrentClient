import java.nio.ByteBuffer;

public class Learning {

    public static void main(String[] args) {

        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putInt(1);
        buffer.putInt(10);
        buffer.putDouble(4.56);
        buffer.flip();
        buffer.mark();
        System.out.println(buffer.getInt());
        System.out.println(buffer.getInt());
        System.out.println(buffer.getDouble());

    }
}
