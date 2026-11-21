public abstract class Room {

    public static final int MAX_BOOKINGS = 50;

    private int number;
    private int floor;
    private double basePrice;                       // ціна за одну ніч
    private Booking[] bookings = new Booking[MAX_BOOKINGS];
    private int bookingsCount = 0;

    public Room(int number, int floor, double basePrice) {
        this.number = number;
        this.floor = floor;
        setBasePrice(basePrice);
    }

    public int getNumber() {
        return number;
    }

    public int getFloor() {
        return floor;
    }

    public void setFloor(int floor) {
        if (floor > 0) {
            this.floor = floor;
        }
    }

    public double getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(double basePrice) {
        if (basePrice > 0) {
            this.basePrice = basePrice;
        } else {
            System.out.println("Ціна має бути більшою за 0!");
        }
    }

    public int getBookingsCount() {
        return bookingsCount;
    }

    public Booking getBooking(int index) {
        return bookings[index];
    }

    public abstract double calculatePrice(int nights);

    public abstract String getType();

    public abstract String getDetails();

    public abstract String getDescription();

    public abstract int getCapacity();

    public abstract int getPrestige();

    public double calculatePrice(int nights, boolean withBreakfast) {
        return calculatePrice(nights);
    }

    public final boolean isFree(int in, int out) {
        for (int i = 0; i < bookingsCount; i++) {
            if (bookings[i].overlaps(in, out)) {
                return false;
            }
        }
        return true;
    }

    public Booking getBookingOn(int day) {
        for (int i = 0; i < bookingsCount; i++) {
            if (day >= bookings[i].getCheckIn() && day < bookings[i].getCheckOut()) {
                return bookings[i];
            }
        }
        return null;
    }

    public boolean isOccupiedOn(int day) {
        return getBookingOn(day) != null;
    }

    public boolean addBooking(Booking booking) {
        if (bookingsCount >= MAX_BOOKINGS) {
            return false;
        }
        bookings[bookingsCount] = booking;
        bookingsCount++;
        return true;
    }

    public boolean removeBooking(int index) {
        if (index < 0 || index >= bookingsCount) {
            return false;
        }
        for (int i = index; i < bookingsCount - 1; i++) {
            bookings[i] = bookings[i + 1];
        }
        bookings[bookingsCount - 1] = null;
        bookingsCount--;
        return true;
    }

    @Override
    public String toString() {
        return "№" + number + " " + getType() + " (" + floor + " поверх), "
                + String.format("%.0f грн/ніч", basePrice);
    }
}
