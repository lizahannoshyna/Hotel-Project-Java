public class DateUtil {

    public static final int BASE_YEAR = 2000;
    public static final int MAX_YEAR = 2100;

    private static final String[] MONTH_NAMES = {
            "Січень", "Лютий", "Березень", "Квітень", "Травень", "Червень",
            "Липень", "Серпень", "Вересень", "Жовтень", "Листопад", "Грудень"
    };

    public static final String[] DAY_NAMES = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд"};

    public static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }

    public static int daysInMonth(int month, int year) {
        switch (month) {
            case 2:
                return isLeapYear(year) ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    public static String getMonthName(int month) {
        return MONTH_NAMES[month - 1];
    }

    public static int toDayNumber(int day, int month, int year) {
        int total = 0;
        for (int y = BASE_YEAR; y < year; y++) {
            total += isLeapYear(y) ? 366 : 365;
        }
        for (int m = 1; m < month; m++) {
            total += daysInMonth(m, year);
        }
        return total + day - 1;
    }

    public static int[] fromDayNumber(int dayNumber) {
        int year = BASE_YEAR;
        while (true) {
            int len = isLeapYear(year) ? 366 : 365;
            if (dayNumber < len) {
                break;
            }
            dayNumber -= len;
            year++;
        }
        int month = 1;
        while (dayNumber >= daysInMonth(month, year)) {
            dayNumber -= daysInMonth(month, year);
            month++;
        }
        return new int[]{dayNumber + 1, month, year};
    }

    public static String format(int dayNumber) {
        int[] d = fromDayNumber(dayNumber);
        return String.format("%02d.%02d.%04d", d[0], d[1], d[2]);
    }

    public static int dayOfWeek(int dayNumber) {
        return (dayNumber + 5) % 7;
    }

    public static int parseNumber(String s) {
        if (s == null || s.length() == 0 || s.length() > 9) {
            return -1;
        }
        int result = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            result = result * 10 + (c - '0');
        }
        return result;
    }

    public static int parseDate(String text) {
        text = text.trim();
        int p1 = text.indexOf('.');
        if (p1 < 0) {
            return -1;
        }
        int p2 = text.indexOf('.', p1 + 1);
        if (p2 < 0) {
            return -1;
        }
        int d = parseNumber(text.substring(0, p1));
        int m = parseNumber(text.substring(p1 + 1, p2));
        int y = parseNumber(text.substring(p2 + 1));
        if (d < 1 || m < 1 || m > 12 || y < BASE_YEAR || y > MAX_YEAR) {
            return -1;
        }
        if (d > daysInMonth(m, y)) {
            return -1;
        }
        return toDayNumber(d, m, y);
    }

    public static String toIso(int dayNumber) {
        int[] d = fromDayNumber(dayNumber);
        return String.format("%04d-%02d-%02d", d[2], d[1], d[0]);
    }

    public static int parseIso(String text) {
        text = text.trim();
        int p1 = text.indexOf('-');
        if (p1 < 0) {
            return -1;
        }
        int p2 = text.indexOf('-', p1 + 1);
        if (p2 < 0) {
            return -1;
        }
        int y = parseNumber(text.substring(0, p1));
        int m = parseNumber(text.substring(p1 + 1, p2));
        int d = parseNumber(text.substring(p2 + 1));
        if (d < 1 || m < 1 || m > 12 || y < BASE_YEAR || y > MAX_YEAR) {
            return -1;
        }
        if (d > daysInMonth(m, y)) {
            return -1;
        }
        return toDayNumber(d, m, y);
    }

    public static int today() {
        return (int) (System.currentTimeMillis() / 86400000L) - 10957;
    }
}
