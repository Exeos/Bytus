import me.exeos.asmplus.utils.RandomUtil;
import me.exeos.bytus.Bytus;

public class Main {

    public static void main(String[] args) {
        new Bytus().bootstrap(args);
    }

    static int forceOverflow(int a, int result, int k) {
        long offset = k * 4294967296L; // k * 2^32
        return (int)(((long)result - a + offset));
    }
}
