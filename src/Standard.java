public class Standard extends Room {

    private int bedsCount;

    public Standard(int number, int floor, double basePrice) {
        this(number, floor, basePrice, 2);
    }

    public Standard(int number, int floor, double basePrice, int bedsCount) {
        super(number, floor, basePrice);
        this.bedsCount = bedsCount;
    }

    public int getBedsCount() {
        return bedsCount;
    }

    public void setBedsCount(int bedsCount) {
        if (bedsCount > 0) {
            this.bedsCount = bedsCount;
        }
    }

    @Override
    public double calculatePrice(int nights) {
        double total = getBasePrice() * nights;
        if (nights >= 7) {
            total = total * 0.90;
        }
        return total;
    }

    @Override
    public String getType() {
        return "Standard";
    }

    @Override
    public String getDetails() {
        return "Ліжок: " + bedsCount + "; знижка 10% від 7 ночей";
    }

    @Override
    public String getDescription() {
        return "Затишний номер для комфортного відпочинку: зручні ліжка, власна ванна кімната, Wi-Fi та кондиціонер. Оптимальний вибір за співвідношенням ціни та якості. Чим довший побут, тим більша знижка.";
    }

    @Override
    public int getCapacity() {
        return bedsCount;
    }

    @Override
    public int getPrestige() {
        return 100;
    }
}
