import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
public class ConsoleApp {

    private static Scanner in = new Scanner(System.in, "UTF-8");
    private static Hotel hotel = new Hotel("Карпатська Зоря", 12);

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        hotel.fillDemoData();

        System.out.println("Ласкаво просимо до системи бронювання готелю \"" + hotel.getName() + "\"!");
        int choice;
        do {
            printMenu();
            choice = readInt("Ваш вибір", 0, 8);
            switch (choice) {
                case 1:
                    showRooms();
                    break;
                case 2:
                    searchFreeRooms();
                    break;
                case 3:
                    bookRoom();
                    break;
                case 4:
                    calculateCost();
                    break;
                case 5:
                    showCalendar();
                    break;
                case 6:
                    cancelBooking();
                    break;
                case 7:
                    hotel.sortByPrice();
                    hotel.printAllSorted();
                    break;
                case 8:
                    hotel.printReport(readDate("Дата для звіту (ДД.ММ.РРРР)"));
                    break;
                case 0:
                    System.out.println("До побачення!");
                    break;
                default:
                    System.out.println("Невідома команда.");
            }
        } while (choice != 0);
    }

    private static void printMenu() {
        System.out.println("\n============ МЕНЮ ============");
        System.out.println("1. Усі номери (статус на дату)");
        System.out.println("2. Пошук вільних номерів на період");
        System.out.println("3. Забронювати номер");
        System.out.println("4. Розрахувати вартість проживання");
        System.out.println("5. Календар зайнятості номера");
        System.out.println("6. Скасувати бронювання");
        System.out.println("7. Сортувати номери за ціною");
        System.out.println("8. Звіт по готелю");
        System.out.println("0. Вихід");
    }

    private static void showRooms() {
        int day = readDate("Показати статус на дату (ДД.ММ.РРРР)");
        hotel.printRooms(day);
    }

    private static void searchFreeRooms() {
        int[] period = readPeriod();
        System.out.println("Тип номера: 1 - будь-який, 2 - Standard, 3 - Lux, 4 - Suite");
        int typeChoice = readInt("Оберіть тип", 1, 4);
        int maxPrice = readInt("Максимальна ціна за ніч, грн (0 - без обмеження)", 0, 1000000);

        String type = "";
        if (typeChoice == 2) {
            type = "Standard";
        } else if (typeChoice == 3) {
            type = "Lux";
        } else if (typeChoice == 4) {
            type = "Suite";
        }

        if (maxPrice > 0) {
            hotel.findFreeRooms(period[0], period[1], type, maxPrice);
        } else if (!type.equals("")) {
            hotel.findFreeRooms(period[0], period[1], type);
        } else {
            hotel.findFreeRooms(period[0], period[1]);
        }
    }

    private static void bookRoom() {
        Room room = readRoom();
        System.out.println("Обрано: " + room + " | " + room.getDetails());

        int[] period = readPeriod();
        if (!room.isFree(period[0], period[1])) {
            System.out.println("На жаль, номер " + room.getNumber() + " уже зайнятий на цей період.");
            System.out.println("Перегляньте календар (пункт 5) або знайдіть інший номер (пункт 2).");
            return;
        }

        String guest = readName("Ім'я гостя");
        boolean breakfast = false;
        if (room instanceof Lux) {
            breakfast = readInt("Додати сніданок? (1 - так, 0 - ні)", 0, 1) == 1;
        }

        int nights = period[1] - period[0];
        double price = room.calculatePrice(nights, breakfast);
        System.out.println("\nГість: " + guest);
        System.out.println("Період: " + DateUtil.format(period[0]) + " - " + DateUtil.format(period[1])
                + " (" + nights + " н.)");
        System.out.printf("До сплати: %.0f грн%n", price);

        if (readInt("Підтвердити бронювання? (1 - так, 0 - ні)", 0, 1) == 0) {
            System.out.println("Бронювання скасовано.");
            return;
        }

        int code = hotel.book(room.getNumber(), guest, period[0], period[1], breakfast);
        switch (code) {
            case 0:
                System.out.println("Номер успішно заброньовано!");
                break;
            case 2:
                System.out.println("Помилка: номер зайнятий.");
                break;
            case 3:
                System.out.println("Помилка: некоректні дати.");
                break;
            case 4:
                System.out.println("Помилка: для номера вичерпано ліміт бронювань.");
                break;
            default:
                System.out.println("Помилка бронювання.");
        }
    }

    private static void calculateCost() {
        Room room = readRoom();
        int nights = readInt("Кількість ночей", 1, 365);
        boolean breakfast = false;
        if (room instanceof Lux) {
            breakfast = readInt("З сніданком? (1 - так, 0 - ні)", 0, 1) == 1;
        }
        double total = room.calculatePrice(nights, breakfast);
        System.out.println("\n--- Розрахунок вартості ---");
        System.out.println(room);
        System.out.printf("Ціна за ніч: %.0f грн%n", room.getBasePrice());
        System.out.println("Ночей: " + nights);
        System.out.printf("Без знижок і доплат: %.0f грн%n", room.getBasePrice() * nights);
        System.out.printf("До сплати: %.0f грн%n", total);
    }

    private static void showCalendar() {
        Room room = readRoom();
        int month = readInt("Місяць (1-12)", 1, 12);
        int year = readInt("Рік", DateUtil.BASE_YEAR, DateUtil.MAX_YEAR);
        hotel.printCalendar(room, month, year);
    }

    private static void cancelBooking() {
        Room room = readRoom();
        if (room.getBookingsCount() == 0) {
            System.out.println("У цього номера немає бронювань.");
            return;
        }
        System.out.println("Бронювання номера " + room.getNumber() + ":");
        for (int i = 0; i < room.getBookingsCount(); i++) {
            System.out.println("  " + (i + 1) + ". " + room.getBooking(i));
        }
        int n = readInt("Номер бронювання для скасування (0 - відміна)", 0, room.getBookingsCount());
        if (n == 0) {
            return;
        }
        if (hotel.cancelBooking(room.getNumber(), n - 1)) {
            System.out.println("Бронювання скасовано.");
        } else {
            System.out.println("Не вдалося скасувати бронювання.");
        }
    }

    private static int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt + ": ");
            int value = DateUtil.parseNumber(in.nextLine().trim());
            if (value >= min && value <= max) {
                return value;
            }
            System.out.println("Помилка! Введіть ціле число від " + min + " до " + max + ".");
        }
    }

    private static int readDate(String prompt) {
        while (true) {
            System.out.print(prompt + ": ");
            int day = DateUtil.parseDate(in.nextLine());
            if (day >= 0) {
                return day;
            }
            System.out.println("Помилка! Введіть існуючу дату у форматі ДД.ММ.РРРР (наприклад, 25.12.2026).");
        }
    }

    private static int[] readPeriod() {
        while (true) {
            int checkIn = readDate("Дата заселення (ДД.ММ.РРРР)");
            int checkOut = readDate("Дата виселення (ДД.ММ.РРРР)");
            if (checkOut > checkIn) {
                return new int[]{checkIn, checkOut};
            }
            System.out.println("Помилка! Дата виселення має бути пізніше дати заселення.");
        }
    }

    private static String readName(String prompt) {
        while (true) {
            System.out.print(prompt + ": ");
            String s = in.nextLine().trim();
            if (s.length() >= 2 && s.length() <= 40) {
                return s;
            }
            System.out.println("Помилка! Ім'я має містити від 2 до 40 символів.");
        }
    }

    private static Room readRoom() {
        while (true) {
            int number = readInt("Номер кімнати (напр. 101, 203, 401)", 1, 99999);
            Room room = hotel.findRoom(number);
            if (room != null) {
                return room;
            }
            System.out.println("Такого номера в готелі немає.");
        }
    }
}
