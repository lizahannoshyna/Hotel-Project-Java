import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class User {

    private String name;
    private String email;
    private String phone;
    private String passwordHash;

    public User(String name, String email, String phone, String plainPassword) {
        this.name = name;
        this.email = email.toLowerCase();
        this.phone = phone;
        this.passwordHash = hash(plainPassword);
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean checkPassword(String plainPassword) {
        return passwordHash.equals(hash(plainPassword));
    }

    private static String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(("hotel-salt:" + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(password.hashCode());
        }
    }
}
