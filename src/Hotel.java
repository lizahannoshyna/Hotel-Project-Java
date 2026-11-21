public class Hotel {

    private String name;
    private Room[] rooms;
    private int roomsCount = 0;

    public Hotel(String name, int capacity) {
        this.name = name;
        this.rooms = new Room[capacity];
    }

    public String getName() {
        return name;
    }

    public int getRoomsCount() {
        return roomsCount;
    }

    public boolean addRoom(Room room) {
        if (roomsCount >= rooms.length || findRoom(room.getNumber()) != null) {
            return false;
        }
        rooms[roomsCount] = room;
        roomsCount++;
        return true;
    }

    public Room findRoom(int number) {
        for (int i = 0; i < roomsCount; i++) {
            if (rooms[i].getNumber() == number) {
                return rooms[i];
            }
        }
        return null;
    }

    public int book(int roomNumber, String guest, String phone, String email, int in, int out, boolean withBreakfast) {
        Room room = findRoom(roomNumber);
        if (room == null) {
            return 1;
        }
        if (out <= in) {
            return 3;
        }
        if (!room.isFree(in, out)) {
            return 2;
        }
        double total = room.calculatePrice(out - in, withBreakfast);
        if (!room.addBooking(new Booking(roomNumber, guest, phone, email, in, out, total))) {
            return 4;
        }
        return 0;
    }

    public int book(int roomNumber, String guest, int in, int out, boolean withBreakfast) {
        return book(roomNumber, guest, "", "", in, out, withBreakfast);
    }

    private void printTableHeader() {
        System.out.printf("%-6s %-10s %-7s %-12s %s%n", "№", "Тип", "Поверх", "Ціна/ніч", "Особливості");
        System.out.println("-------------------------------------------------------------------------");
    }

    private void printRoomRow(Room r) {
        System.out.printf("%-6d %-10s %-7d %-12s %s%n", r.getNumber(), r.getType(), r.getFloor(),
                String.format("%.0f грн", r.getBasePrice()), r.getDetails());
    }

    public void printRooms(int day) {
        System.out.println("\n=== Номери готелю \"" + name + "\" на " + DateUtil.format(day) + " ===");
        System.out.printf("%-6s %-10s %-12s %s%n", "№", "Тип", "Ціна/ніч", "Статус");
        System.out.println("-------------------------------------------------------------------------");
        for (int i = 0; i < roomsCount; i++) {
            Room r = rooms[i];
            Booking b = r.getBookingOn(day);
            String status;
            if (b == null) {
                status = "вільний";
            } else {
                status = "ЗАЙНЯТИЙ (" + b.getGuestName() + ", до " + DateUtil.format(b.getCheckOut()) + ")";
            }
            System.out.printf("%-6d %-10s %-12s %s%n", r.getNumber(), r.getType(),
                    String.format("%.0f грн", r.getBasePrice()), status);
        }
    }

    public int findFreeRooms(int in, int out) {
        return findFreeRooms(in, out, "", 0);
    }

    public int findFreeRooms(int in, int out, String type) {
        return findFreeRooms(in, out, type, 0);
    }

    public int findFreeRooms(int in, int out, String type, double maxPrice) {
        int nights = out - in;
        int found = 0;
        System.out.println("\nВільні номери з " + DateUtil.format(in) + " по " + DateUtil.format(out)
                + " (" + nights + " н.):");
        System.out.printf("%-6s %-10s %-12s %s%n", "№", "Тип", "Ціна/ніч", "Вартість за період");
        System.out.println("-------------------------------------------------------------------------");
        for (int i = 0; i < roomsCount; i++) {
            Room r = rooms[i];
            boolean typeOk = type.equals("") || r.getType().equals(type);
            boolean priceOk = maxPrice <= 0 || r.getBasePrice() <= maxPrice;
            if (typeOk && priceOk && r.isFree(in, out)) {
                System.out.printf("%-6d %-10s %-12s %s%n", r.getNumber(), r.getType(),
                        String.format("%.0f грн", r.getBasePrice()),
                        String.format("%.0f грн", r.calculatePrice(nights)));
                found++;
            }
        }
        if (found == 0) {
            System.out.println("Нічого не знайдено.");
        }
        return found;
    }

    public void printCalendar(Room room, int month, int year) {
        System.out.println("\nНомер " + room.getNumber() + " (" + room.getType() + ") - "
                + DateUtil.getMonthName(month) + " " + year);
        for (int i = 0; i < 7; i++) {
            System.out.printf("%-4s", DateUtil.DAY_NAMES[i]);
        }
        System.out.println();

        int first = DateUtil.toDayNumber(1, month, year);
        int offset = DateUtil.dayOfWeek(first);
        for (int i = 0; i < offset; i++) {
            System.out.print("    ");
        }
        int days = DateUtil.daysInMonth(month, year);
        for (int d = 1; d <= days; d++) {
            boolean busy = room.isOccupiedOn(first + d - 1);
            System.out.print(busy ? String.format("%2d* ", d) : String.format("%2d  ", d));
            if ((offset + d) % 7 == 0) {
                System.out.println();
            }
        }
        System.out.println();
        System.out.println("* - ніч зайнята");

        if (room.getBookingsCount() > 0) {
            System.out.println("Бронювання номера:");
            for (int i = 0; i < room.getBookingsCount(); i++) {
                System.out.println("  " + (i + 1) + ". " + room.getBooking(i));
            }
        }
    }

    public boolean cancelBooking(int roomNumber, int bookingIndex) {
        Room room = findRoom(roomNumber);
        if (room == null) {
            return false;
        }
        return room.removeBooking(bookingIndex);
    }

    public void sortByPrice() {
        for (int i = 0; i < roomsCount - 1; i++) {
            for (int j = 0; j < roomsCount - 1 - i; j++) {
                if (rooms[j].getBasePrice() > rooms[j + 1].getBasePrice()) {
                    Room tmp = rooms[j];
                    rooms[j] = rooms[j + 1];
                    rooms[j + 1] = tmp;
                }
            }
        }
    }

    public void printAllSorted() {
        System.out.println("\n=== Номери за зростанням ціни ===");
        printTableHeader();
        for (Room r : getRoomsArray()) {
            printRoomRow(r);
        }
    }

    private Room[] getRoomsArray() {
        Room[] copy = new Room[roomsCount];
        for (int i = 0; i < roomsCount; i++) {
            copy[i] = rooms[i];
        }
        return copy;
    }

    public void printReport(int day) {
        int occupied = 0;
        int totalBookings = 0;
        double revenue = 0;
        double revenueStandard = 0;
        double revenueLux = 0;
        double revenueSuite = 0;

        for (Room r : getRoomsArray()) {
            if (r.isOccupiedOn(day)) {
                occupied++;
            }
            for (int i = 0; i < r.getBookingsCount(); i++) {
                double price = r.getBooking(i).getTotalPrice();
                revenue += price;
                totalBookings++;
                if (r.getType().equals("Standard")) {
                    revenueStandard += price;
                } else if (r.getType().equals("Lux")) {
                    revenueLux += price;
                } else {
                    revenueSuite += price;
                }
            }
        }

        double percent = roomsCount == 0 ? 0 : occupied * 100.0 / roomsCount;
        System.out.println("\n=========== ЗВІТ ГОТЕЛЮ \"" + name + "\" ===========");
        System.out.println("Дата звіту: " + DateUtil.format(day));
        System.out.println("Усього номерів: " + roomsCount);
        System.out.printf("Зайнято на цю дату: %d (%.1f%%)%n", occupied, percent);
        System.out.println("Вільно на цю дату: " + (roomsCount - occupied));
        System.out.println("Усього бронювань: " + totalBookings);
        System.out.printf("Загальна сума бронювань: %.0f грн%n", revenue);
        System.out.printf("  Standard: %.0f грн%n", revenueStandard);
        System.out.printf("  Lux:      %.0f грн%n", revenueLux);
        System.out.printf("  Suite:    %.0f грн%n", revenueSuite);

        System.out.println("\nСписок усіх бронювань:");
        boolean any = false;
        for (Room r : getRoomsArray()) {
            for (int i = 0; i < r.getBookingsCount(); i++) {
                System.out.println("  Номер " + r.getNumber() + " | " + r.getBooking(i));
                any = true;
            }
        }
        if (!any) {
            System.out.println("  (немає)");
        }
    }


    public Room getRoom(int index) {
        return rooms[index];
    }

    public Room[] getFreeRooms(int in, int out) {
        return getFreeRooms(in, out, "", 0, 0);
    }

    public Room[] getFreeRooms(int in, int out, String type, double maxPrice) {
        return getFreeRooms(in, out, type, maxPrice, 0);
    }

    public Room[] getFreeRooms(int in, int out, String type, double maxPrice, int minPlaces) {
        Room[] temp = new Room[roomsCount];
        int count = 0;
        for (int i = 0; i < roomsCount; i++) {
            Room r = rooms[i];
            boolean typeOk = type.equals("") || r.getType().equals(type);
            boolean priceOk = maxPrice <= 0 || r.getBasePrice() <= maxPrice;
            boolean placesOk = r.getCapacity() >= minPlaces;
            if (typeOk && priceOk && placesOk && r.isFree(in, out)) {
                temp[count] = r;
                count++;
            }
        }
        Room[] result = new Room[count];
        for (int i = 0; i < count; i++) {
            result[i] = temp[i];
        }
        return result;
    }

    public Room[] filterRooms(String type, double maxPrice, int minPlaces) {
        Room[] temp = new Room[roomsCount];
        int count = 0;
        for (int i = 0; i < roomsCount; i++) {
            Room r = rooms[i];
            boolean typeOk = type.equals("") || r.getType().equals(type);
            boolean priceOk = maxPrice <= 0 || r.getBasePrice() <= maxPrice;
            boolean placesOk = r.getCapacity() >= minPlaces;
            if (typeOk && priceOk && placesOk) {
                temp[count] = r;
                count++;
            }
        }
        Room[] result = new Room[count];
        for (int i = 0; i < count; i++) {
            result[i] = temp[i];
        }
        return result;
    }

    public void sortRooms(Room[] arr, String mode) {
        for (int i = 0; i < arr.length - 1; i++) {
            for (int j = 0; j < arr.length - 1 - i; j++) {
                if (shouldSwap(arr[j], arr[j + 1], mode)) {
                    Room tmp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = tmp;
                }
            }
        }
    }

    private boolean shouldSwap(Room a, Room b, String mode) {
        switch (mode) {
            case "price_asc":
                return a.getBasePrice() > b.getBasePrice();
            case "price_desc":
                return a.getBasePrice() < b.getBasePrice();
            case "prestige_desc":
                if (a.getPrestige() != b.getPrestige()) {
                    return a.getPrestige() < b.getPrestige();
                }
                return a.getBasePrice() < b.getBasePrice();
            case "prestige_asc":
                if (a.getPrestige() != b.getPrestige()) {
                    return a.getPrestige() > b.getPrestige();
                }
                return a.getBasePrice() > b.getBasePrice();
            default:
                return a.getNumber() > b.getNumber();
        }
    }

    private User[] users = new User[200];
    private int usersCount = 0;

    public boolean registerUser(User user) {
        if (usersCount >= users.length || findUser(user.getEmail()) != null) {
            return false;
        }
        users[usersCount] = user;
        usersCount++;
        return true;
    }

    public User findUser(String email) {
        for (int i = 0; i < usersCount; i++) {
            if (users[i].getEmail().equalsIgnoreCase(email)) {
                return users[i];
            }
        }
        return null;
    }

    public User authenticate(String email, String password) {
        User u = findUser(email);
        if (u != null && u.checkPassword(password)) {
            return u;
        }
        return null;
    }

    public int getUsersCount() {
        return usersCount;
    }

    public User getUser(int index) {
        return users[index];
    }

    public Booking findBooking(int roomNumber, int checkIn) {
        Room room = findRoom(roomNumber);
        if (room == null) {
            return null;
        }
        for (int i = 0; i < room.getBookingsCount(); i++) {
            if (room.getBooking(i).getCheckIn() == checkIn) {
                return room.getBooking(i);
            }
        }
        return null;
    }

    public Booking[] getAllBookings() {
        int total = 0;
        for (int i = 0; i < roomsCount; i++) {
            total += rooms[i].getBookingsCount();
        }
        Booking[] all = new Booking[total];
        int k = 0;
        for (int i = 0; i < roomsCount; i++) {
            for (int j = 0; j < rooms[i].getBookingsCount(); j++) {
                all[k] = rooms[i].getBooking(j);
                k++;
            }
        }
        for (int i = 0; i < total - 1; i++) {
            for (int j = 0; j < total - 1 - i; j++) {
                if (all[j].getCheckIn() > all[j + 1].getCheckIn()) {
                    Booking tmp = all[j];
                    all[j] = all[j + 1];
                    all[j + 1] = tmp;
                }
            }
        }
        return all;
    }

    public boolean cancelBookingByDate(int roomNumber, int checkIn) {
        Room room = findRoom(roomNumber);
        if (room == null) {
            return false;
        }
        for (int i = 0; i < room.getBookingsCount(); i++) {
            if (room.getBooking(i).getCheckIn() == checkIn) {
                return room.removeBooking(i);
            }
        }
        return false;
    }


    public void fillDemoData() {
        addRoom(new Standard(101, 1, 1200, 1));
        addRoom(new Standard(102, 1, 1200));
        addRoom(new Standard(103, 1, 1400, 3));
        addRoom(new Standard(201, 2, 1300, 2));
        addRoom(new Standard(202, 2, 1500, 4));
        addRoom(new Lux(203, 2, 3000));
        addRoom(new Lux(301, 3, 3500, true, true));
        addRoom(new Lux(302, 3, 3200, true, false, 3));
        addRoom(new Suite(401, 4, 5500, 3));
        addRoom(new Suite(402, 4, 6500, 4));

        registerUser(new User("Андрій Петренко", "guest@example.com", "+380501112233", "guest123"));

        int t = DateUtil.today();
        book(101, "Іваненко Олег", "+380671110001", "oleg@example.com", t - 2, t + 3, false);
        book(101, "Коваль Марія", "+380671110002", "maria@example.com", t + 10, t + 13, false);
        book(102, "Сидоренко Анна", "+380671110003", "anna@example.com", t, t + 2, false);
        book(103, "Бондар Василь", "+380671110004", "vasyl@example.com", t + 1, t + 8, false);
        book(201, "Гриценко Юлія", "+380671110005", "yulia@example.com", t - 1, t + 1, false);
        book(202, "Ткаченко Іван", "+380671110006", "ivan@example.com", t - 4, t, false);
        book(203, "Андрій Петренко", "+380501112233", "guest@example.com", t + 2, t + 8, true);
        book(301, "Шевченко Ірина", "+380671110007", "iryna@example.com", t - 3, t + 7, true);
        book(301, "Лисенко Роман", "+380671110008", "roman@example.com", t + 9, t + 12, true);
        book(302, "Кравчук Олена", "+380671110009", "olena@example.com", t + 5, t + 9, false);
        book(401, "Мельник Сергій", "+380671110010", "serhii@example.com", t + 4, t + 11, false);
        book(402, "Андрій Петренко", "+380501112233", "guest@example.com", t + 14, t + 17, false);
    }
}
