public class Suite extends Room {

    private int roomsCount;

    public Suite(int number, int floor, double basePrice, int roomsCount) {
        super(number, floor, basePrice);
        this.roomsCount = roomsCount;
    }

    public int getRoomsCount() {
        return roomsCount;
    }

    @Override
    public double calculatePrice(int nights) {
        int paidNights = nights - nights / 7;      // кожна 7-ма ніч у подарунок
        return getBasePrice() * paidNights;
    }

    @Override
    public String getType() {
        return "Suite";
    }

    @Override
    public String getDetails() {
        return "Кімнат: " + roomsCount + ", місць: " + getCapacity() + "; кожна 7-ма ніч безкоштовно";
    }

    @Override
    public String getDescription() {
        return "Багатокімнатні апартаменти з вітальнею, спальнями та кухонною зоною. Ідеально підходять для сімей і компаній. Кожна сьома ніч проживання безкоштовна.";
    }

    @Override
    public int getCapacity() {
        return roomsCount * 2;
    }

    @Override
    public int getPrestige() {
        return 300 + 10 * roomsCount;
    }
}
