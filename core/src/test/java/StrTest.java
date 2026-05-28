public class StrTest {

    private static int[] keys;
    static {
        keys = new int[3];
        keys[0] = 67;
        keys[1] = 69;
        keys[2] = 1337;
    }

    public static void main(String[] args) {
        System.out.println(crypt("Hello World", 0));
    }

    public static void xD() {
        String x = "awdaw";
    }

    public static String crypt(String x, int keyIndex) {
        char[] chars = x.toCharArray();
        char[] newChars = new char[chars.length];

        for (int i = 0; i < chars.length; i++) {
            newChars[i] = (char) ((int) chars[i] ^ keys[keyIndex]);
        }

        return new String(newChars);
    }
}
