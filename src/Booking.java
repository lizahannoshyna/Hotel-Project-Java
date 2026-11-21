public class Booking {

    private int roomNumber;
    private String guestName;
    private String phone;
    private String email;
    private int checkIn;
    private int checkOut;
    private double totalPrice;

    public Booking(int roomNumber, String guestName, String phone, String email, int checkIn, int checkOut, double totalPrice) {
        this.roomNumber = roomNumber;
        this.guestName = guestName;
        this.phone = phone;
        this.email = email;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.totalPrice = totalPrice;
    }

    public int getRoomNumber() {
        return roomNumber;
    }

    public String getGuestName() {
        return guestName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public int getCheckIn() {
        return checkIn;
    }

    public int getCheckOut() {
        return checkOut;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public int getNights() {
        return checkOut - checkIn;
    }

    public boolean overlaps(int in, int out) {
        return in < checkOut && out > checkIn;
    }

    @Override
    public String toString() {
        return guestName + ": " + DateUtil.format(checkIn) + " - " + DateUtil.format(checkOut)
                + " (" + getNights() + " н.), " + String.format("%.0f грн", totalPrice);
    }
}
