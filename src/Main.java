import java.io.IOException;

public class Main {

    private static final String ADMIN_EMAIL = "admin@hotel.com";
    private static final String ADMIN_PASSWORD = "admin123";

    public static void main(String[] args) throws IOException {
        Hotel hotel = new Hotel("Карпатська Зоря", 40);
        hotel.fillDemoData();

        WebServer server = new WebServer(hotel, 8080, ADMIN_EMAIL, ADMIN_PASSWORD);
        server.start();

        System.out.println("Сервер запущено!");
        System.out.println("Сайт:  http://localhost:8080   -> кнопка \"Увійти\"");
        System.out.println("Гість: guest@example.com / guest123  (відкриється меню клієнта)");
        System.out.println("Адмін: " + ADMIN_EMAIL + " / " + ADMIN_PASSWORD + "  (відкриється панель адміністратора)");
        System.out.println("Щоб зупинити сервер - натисніть червоний квадрат (Stop) в IntelliJ.");
    }
}
