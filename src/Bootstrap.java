import com.bytus.Bytus;

public class Bootstrap {

    public static void main(String[] args) {
        try {
            new Bytus().start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
