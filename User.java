/** A login account. Role is ADMIN (full access) or STAFF (warehouse staff, limited access). */
public class User {
    public static final String ADMIN = "ADMIN";
    public static final String STAFF = "STAFF";

    private final String username;
    private final String fullName;
    private final String role;

    public User(String username, String fullName, String role) {
        this.username = username;
        this.fullName = fullName == null ? "" : fullName;
        this.role = STAFF.equalsIgnoreCase(role) ? STAFF : ADMIN;
    }

    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
    public boolean isAdmin() { return ADMIN.equals(role); }
    public String roleLabel() { return isAdmin() ? "Admin" : "Warehouse Staff"; }
}
