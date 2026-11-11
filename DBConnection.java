import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

/**
 * JDBC connection helper. On first run it creates the database, all tables
 * and inserts sample data plus a default admin account.
 */
public class DBConnection {

    // ====== CHANGE THESE TO MATCH YOUR MYSQL SETUP ======
    private static final String HOST = "localhost";
    private static final String PORT = "3306";
    private static final String DB_NAME = "warehouse_db";
    private static final String USER = "root";
    private static final String PASSWORD = "Soomro123@";   // <-- put your MySQL password here
    // =====================================================

    private static final String PARAMS =
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:mysql://" + HOST + ":" + PORT + "/" + DB_NAME + PARAMS, USER, PASSWORD);
    }

    /** Creates database + tables + seed data if they do not exist yet. */
    public static void initialize() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC driver not found. Add the mysql-connector-j JAR "
                    + "to your project libraries (see README).", e);
        }

        // Step 1: create the database itself
        try (Connection c = DriverManager.getConnection(
                "jdbc:mysql://" + HOST + ":" + PORT + "/" + PARAMS, USER, PASSWORD);
             Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DB_NAME + " CHARACTER SET utf8mb4");
        }

        // Step 2: create tables and seed data
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS users ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "username VARCHAR(50) UNIQUE NOT NULL,"
                    + "password_hash CHAR(64) NOT NULL,"
                    + "full_name VARCHAR(100),"
                    + "role VARCHAR(20) NOT NULL DEFAULT 'ADMIN')");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS products ("
                    + "product_id VARCHAR(20) PRIMARY KEY,"
                    + "name VARCHAR(100) NOT NULL,"
                    + "category VARCHAR(50) NOT NULL,"
                    + "quantity INT NOT NULL DEFAULT 0,"
                    + "price DECIMAL(10,2) NOT NULL DEFAULT 0,"
                    + "description VARCHAR(255) NULL,"
                    + "min_stock INT NOT NULL DEFAULT 30,"
                    + "image_path VARCHAR(500) NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS stock_history ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "event_time TIMESTAMP NULL,"
                    + "product_name VARCHAR(100),"
                    + "action VARCHAR(20),"
                    + "quantity INT,"
                    + "performed_by VARCHAR(50),"
                    + "note VARCHAR(100),"
                    + "batch_id VARCHAR(20) NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS categories ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "name VARCHAR(50) UNIQUE NOT NULL,"
                    + "description VARCHAR(255) NULL)");
            // The warehouse stack: every row is one batch, position 0 = BOTTOM, highest position = TOP
            st.executeUpdate("CREATE TABLE IF NOT EXISTS batches ("
                    + "batch_id VARCHAR(20) PRIMARY KEY,"
                    + "product_id VARCHAR(20) NOT NULL,"
                    + "quantity INT NOT NULL,"
                    + "batch_date DATE NOT NULL,"
                    + "supplier VARCHAR(100) NULL,"
                    + "unit_price DECIMAL(10,2) NOT NULL DEFAULT 0,"
                    + "position INT NOT NULL,"
                    + "created_by VARCHAR(50) NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS stack_items ("
                    + "position INT PRIMARY KEY,"
                    + "product_id VARCHAR(20),"
                    + "name VARCHAR(100),"
                    + "category VARCHAR(50),"
                    + "quantity INT,"
                    + "price DECIMAL(10,2))");
            // Upgrades for databases created by older versions of this project
            addColumn(st, "stock_history", "note VARCHAR(100) NULL");
            addColumn(st, "products", "description VARCHAR(255) NULL");
            addColumn(st, "products", "min_stock INT NOT NULL DEFAULT 30");
            addColumn(st, "products", "image_path VARCHAR(500) NULL");
            addColumn(st, "stock_history", "batch_id VARCHAR(20) NULL");
            boolean roleAdded = addColumn(st, "users", "role VARCHAR(20) NOT NULL DEFAULT 'ADMIN'");
            seedUsers(c, roleAdded);
            seedProducts(c);
            seedCategories(c);
            ensureOpeningBatches(c);
        }
    }

    /** @return true if the column was added, false if it already existed */
    private static boolean addColumn(Statement st, String table, String definition) throws SQLException {
        try {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + definition);
            return true;
        } catch (SQLException ex) {
            if (ex.getErrorCode() != 1060) throw ex;   // 1060 = column already exists
            return false;
        }
    }

    private static boolean isEmpty(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1) == 0;
        }
    }

    private static void seedUsers(Connection c, boolean roleJustAdded) throws SQLException {
        boolean fresh = isEmpty(c, "users");
        if (!fresh && !roleJustAdded) return;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT IGNORE INTO users(username,password_hash,full_name,role) VALUES(?,?,?,?)")) {
            if (fresh) {
                ps.setString(1, "admin");
                ps.setString(2, hash("admin123"));
                ps.setString(3, "System Administrator");
                ps.setString(4, "ADMIN");
                ps.executeUpdate();
            }
            ps.setString(1, "staff");
            ps.setString(2, hash("staff123"));
            ps.setString(3, "Warehouse Staff");
            ps.setString(4, "STAFF");
            ps.executeUpdate();
        }
    }

    /** Fills the categories table from the products that already exist plus a few common defaults. */
    private static void seedCategories(Connection c) throws SQLException {
        if (!isEmpty(c, "categories")) return;
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT category FROM products ORDER BY category")) {
            while (rs.next()) names.add(rs.getString(1));
        }
        for (String d : new String[]{"Electronics", "Stationery", "Furniture", "Accessories", "Tools", "Other"}) names.add(d);
        try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO categories(name) VALUES(?)")) {
            for (String n : names) {
                ps.setString(1, n);
                ps.executeUpdate();
            }
        }
    }

    /**
     * First start after upgrading (or on a fresh database): every product that already has stock gets one
     * "Opening Stock" batch, pushed onto the stack in product-id order, so quantity == sum of its batches.
     */
    private static void ensureOpeningBatches(Connection c) throws SQLException {
        if (!isEmpty(c, "batches")) return;
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT product_id, quantity, price FROM products WHERE quantity > 0 ORDER BY product_id");
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO batches(batch_id,product_id,quantity,batch_date,supplier,unit_price,position,created_by) "
                             + "VALUES(?,?,?,?,?,?,?,?)")) {
            int n = 0;
            while (rs.next() && n < ProductManager.STACK_CAPACITY) {
                ps.setString(1, String.format("B%03d", n + 1));
                ps.setString(2, rs.getString(1));
                ps.setInt(3, rs.getInt(2));
                ps.setDate(4, java.sql.Date.valueOf(java.time.LocalDate.now()));
                ps.setString(5, "Opening Stock");
                ps.setDouble(6, rs.getDouble(3));
                ps.setInt(7, n);
                ps.setString(8, "System");
                ps.executeUpdate();
                n++;
            }
        }
    }

    private static void seedProducts(Connection c) throws SQLException {
        if (!isEmpty(c, "products")) return;
        Object[][] sample = {
                {"P001", "Laptop", "Electronics", 50, 800.00},
                {"P002", "Mouse", "Electronics", 120, 25.00},
                {"P003", "Keyboard", "Electronics", 100, 45.00},
                {"P004", "Printer", "Electronics", 30, 250.00},
                {"P005", "Notebook", "Stationery", 200, 5.00},
                {"P006", "Pen", "Stationery", 300, 2.00},
                {"P007", "Chair", "Furniture", 40, 120.00},
                {"P008", "Table", "Furniture", 20, 200.00},
                {"P009", "Stapler", "Stationery", 0, 8.00},
                {"P010", "Monitor", "Electronics", 45, 150.00}
        };
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO products(product_id,name,category,quantity,price) VALUES(?,?,?,?,?)");
             PreparedStatement hs = c.prepareStatement(
                     "INSERT INTO stock_history(event_time,product_name,action,quantity,performed_by) "
                             + "VALUES(?,?,?,?,?)")) {
            long now = System.currentTimeMillis();
            int i = 0;
            for (Object[] r : sample) {
                ps.setString(1, (String) r[0]);
                ps.setString(2, (String) r[1]);
                ps.setString(3, (String) r[2]);
                ps.setInt(4, (Integer) r[3]);
                ps.setDouble(5, (Double) r[4]);
                ps.executeUpdate();

                hs.setTimestamp(1, new Timestamp(now - (sample.length - i) * 60_000L));
                hs.setString(2, (String) r[1]);
                hs.setString(3, "Added");
                hs.setInt(4, (Integer) r[3]);
                hs.setString(5, "admin");
                hs.executeUpdate();
                i++;
            }
        }
    }

    /** SHA-256 hex hash, so passwords are never stored in plain text. */
    public static String hash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
