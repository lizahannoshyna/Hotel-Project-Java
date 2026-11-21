public class Validator {

    public static boolean isValidName(String s) {
        return s != null && s.trim().length() >= 2 && s.trim().length() <= 40;
    }

    public static boolean isValidPassword(String s) {
        return s != null && s.length() >= 6 && s.length() <= 60;
    }

    public static boolean isValidEmail(String s) {
        if (s == null || s.length() < 5 || s.length() > 60) {
            return false;
        }
        int at = s.indexOf('@');
        if (at < 1 || at != s.lastIndexOf('@')) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ' ') {
                return false;
            }
        }
        String domain = s.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        return dot > 0 && dot < domain.length() - 1;
    }

    public static String cleanPhone(String s) {
        StringBuilder sb = new StringBuilder();
        int digits = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
                digits++;
            } else if (c == '+' && sb.length() == 0) {
                sb.append(c);
            } else if (c == ' ' || c == '-' || c == '(' || c == ')') {
                continue;
            } else {
                return "";
            }
        }
        if (digits < 10 || digits > 13) {
            return "";
        }
        return sb.toString();
    }
}
