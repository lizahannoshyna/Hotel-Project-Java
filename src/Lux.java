public class Lux extends Room {

    public static final double BREAKFAST_PRICE = 250.0;   // за одну ніч

    private boolean hasJacuzzi;
    private boolean breakfastIncluded;
    private int places;

    public Lux(int number, int floor, double basePrice) {
        this(number, floor, basePrice, false, false);
    }

    public Lux(int number, int floor, double basePrice, boolean hasJacuzzi, boolean breakfastIncluded) {
        this(number, floor, basePrice, hasJacuzzi, breakfastIncluded, 2);
    }

    public Lux(int number, int floor, double basePrice, boolean hasJacuzzi, boolean breakfastIncluded, int places) {
        super(number, floor, basePrice);
        this.hasJacuzzi = hasJacuzzi;
        this.breakfastIncluded = breakfastIncluded;
        this.places = places;
    }

    public boolean hasJacuzzi() {
        return hasJacuzzi;
    }

    public boolean isBreakfastIncluded() {
        return breakfastIncluded;
    }

    public void setBreakfastIncluded(boolean breakfastIncluded) {
        this.breakfastIncluded = breakfastIncluded;
    }

    @Override
    public double calculatePrice(int nights) {
        return calculatePrice(nights, breakfastIncluded);
    }

    @Override
    public double calculatePrice(int nights, boolean withBreakfast) {
        double total = getBasePrice() * nights;
        if (withBreakfast) {
            total = total + BREAKFAST_PRICE * nights;
        }
        if (nights >= 10) {
            total = total * 0.95;
        }
        return total;
    }

    @Override
    public String getType() {
        return "Lux";
    }

    @Override
    public String getDetails() {
        String s = "місць: " + places + ", " + (hasJacuzzi ? "джакузі" : "без джакузі");
        s += breakfastIncluded ? ", сніданок включено" : ", сніданок +" + (int) BREAKFAST_PRICE + " грн/ніч";
        return s;
    }

    @Override
    public String getDescription() {
        return "Просторий номер підвищеного комфорту з окремою зоною відпочинку, преміальною постільною білизною та краєвидом на гори. Можна замовити сніданок у ресторані готелю. Для тривалого проживання діє знижка.";
    }

    @Override
    public int getCapacity() {
        return places;
    }

    @Override
    public int getPrestige() {
        int p = 200;
        if (hasJacuzzi) {
            p += 30;
        }
        if (breakfastIncluded) {
            p += 20;
        }
        return p;
    }
}
