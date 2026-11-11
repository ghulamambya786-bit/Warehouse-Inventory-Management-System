# Warehouse Inventory Management System (Java Swing + MySQL + Custom Stack)

## Files
Main, LoginFrame, Dashboard, Product, ProductManager, CustomStack, InventoryPanel, AddProductPanel,
StockMovementPanel, StockInPanel, StockOutPanel, StackPanel, ReportsPanel, StockHistoryPanel, SettingsPanel,
DBConnection, Theme, RoundPanel, Icons, Images, ImageUtil, SearchBox, Pager, PdfExport, PieChartPanel, LineChartPanel,
BarChartPanel, Refreshable, schema.sql
**New:** Batch, User, BatchesPanel, CategoriesPanel, UsersPanel, TransactionsPanel, CsvUtil

## Requirements
1. JDK 11 or newer
2. MySQL Server 8.x running on localhost:3306
3. MySQL Connector/J JAR (download "Platform Independent" from https://dev.mysql.com/downloads/connector/j/)

## Setup
1. Open `DBConnection.java` and set `USER` and `PASSWORD` to your MySQL login.
2. Add the Connector/J JAR to the project (below).
3. Run `Main`. The first run auto-creates `warehouse_db`, all tables, 10 sample products and the admin account.
   (`schema.sql` is optional - only if you want to create the database by hand.)
4. Login: **Admin: admin / admin123**   |   **Warehouse Staff: staff / staff123**

## Adding the JAR
- **NetBeans:** right-click project > Properties > Libraries > Add JAR/Folder. Run Main.java (Shift+F6).
- **IntelliJ IDEA:** File > Project Structure > Libraries > + > Java > pick the JAR. Right-click Main > Run.
- **Eclipse:** right-click project > Build Path > Add External Archives. Run As > Java Application on Main.
- **Command line (Windows):**
  `javac -cp ".;mysql-connector-j-8.x.x.jar" *.java` then `java -cp ".;mysql-connector-j-8.x.x.jar" Main`
  (Linux/macOS: use `:` instead of `;`)

## Features
1. **Dashboard** - total products, total batches, stock units, inventory value, low stock, trend, recent activity.
2. **Product Management** - add, update, delete (Admin), search. Quantity is read-only: it changes only through PUSH / POP.
3. **Category Management** (Admin) - add, rename, delete categories. A category that still has products cannot be deleted.
4. **Stock In = PUSH** - a new batch (Batch ID, product, quantity, date, supplier, price) goes on top of the stack.
5. **Stock Out = POP** - the latest (TOP) batch is removed; the product quantity goes down by the batch quantity.
6. **Stack Visualization** - all batches from TOP to BOTTOM.
7. **Stock History** - record of every PUSH and POP (with batch id).
8. **Low Stock Alerts** - dashboard banner, bell counter, Low Stock status.
9. **PEEK** - details of the TOP batch.
10. **Stack Search** - find a batch by Batch ID, product or supplier; shows the position from TOP.
11. **Sorting** - Inventory by name, quantity, price; Batches by id, product, quantity, date, supplier.
12. **Batch Management** - Batches page: search, sort, edit batch date / supplier, export CSV.
13. **Reports & Analytics** - charts + Transactions & Reports page: Stock In report, Stock Out report, Available Stock report (date range, CSV).
14. **Transaction History** - date, product, action, quantity, batch, user and details of every operation.
15. **Login & User Management** - roles *Admin* (everything, plus Categories and Users pages) and *Warehouse Staff*
    (cannot delete products, manage categories or users). Admin can add users, change roles, reset passwords.

## How batches and the stack work
- Product quantity = sum of the quantities of its batches. Products that already had stock get one "Opening Stock" batch
  the first time the new version starts (and when a product is added with an opening quantity).
- Stack capacity is 10 batches (`ProductManager.STACK_CAPACITY`). The stack is mirrored in the MySQL table `batches`.
- PUSH and POP are written to the database in one transaction; if it fails the in-memory stack is rolled back.
- Searching the stack and removing a deleted product's batches use only POP and PUSH (helper stack), so the order of the
  other batches never changes.

## Stack implementation (CustomStack.java)
A fixed-capacity `Batch[]` array plus an int `top`. No built-in stack collections.

| Operation | Rule | Time |
|-----------|------|------|
| push(b)   | if full -> StackOverflowException; `top = top + 1; data[top] = b` | O(1) |
| pop()     | if empty -> StackUnderflowException; read `data[top]`, clear it, `top = top - 1` | O(1) |
| peek()    | if empty -> StackUnderflowException; return `data[top]` | O(1) |
| isEmpty() | `top == -1` | O(1) |
| isFull()  | `top == capacity - 1` | O(1) |
| search / removeWhere | pop everything into a helper stack, push it back | O(n) |

Space: O(capacity).

## Look & feel (new design)
Colors: Navy `#082B49`, Turquoise `#00A6A6`, Golden Yellow `#FFBE0B`, Orange `#FB8500`, Red `#E63946`, Light Blue `#EAF4FF`
(all in `Theme.java`). Pictures (login photo, logo, box, cart, laptop) are embedded in `Images.java`, and all icons are
drawn in code (`Icons.java`), so no image files are needed.
- Login: Remember Me keeps the username; Forgot Password shows how to reset.
- Inventory: search, category and status filters, 8 rows per page, Export (CSV). Double-click a row to view details.
- Add Product: Choose Image copies the picture into a `product_images` folder next to where you run the app.
- Reports: Export PDF saves the report page as a one-page PDF; Export CSV saves the data.
- Settings: change your password.

## Stack Visualization (PUSH / POP)
- **Stock In (PUSH):** choose the product, enter quantity, unit price, batch date and supplier. A new batch is pushed on TOP.
- **Stock Out (POP):** shows the TOP batch and removes it after you confirm (reason: Sale, Damaged, ...). The stack is LIFO,
  so only the latest batch can leave.
- Both are written to Stock History / Transactions and update Dashboard, Inventory, Batches and Reports.
- Existing databases are upgraded automatically (new columns and tables are added, old stock becomes "Opening Stock" batches).
  Existing accounts keep full Admin rights; a `staff` account is added.

## Notes
- Passwords are stored as SHA-256 hashes.
- All SQL uses PreparedStatement (no SQL injection).
- Low stock = quantity <= the product's Minimum Stock (default 30 for old products, 10 in the Add Product form).
- PUSH and POP are the stock movements: they change product quantities (Stock In = PUSH, Stock Out = POP).

## Animations
Only a few light animations are used (plain Swing timers, see `Anim.java`):

* **Login:** a wrong password makes the window shake once.
* **Page change:** a quick fade between pages; the sidebar highlight grows from the left.
* **Dashboard:** the numbers count up; the donut chart and the trend line draw themselves.
* **Reports:** pie charts sweep open and bars rise from the axis.
* **Stack Visualization:** a PUSH makes the new batch drop in while the older bars slide down; a POP makes the removed
  batch fly up and fade out while the rest slide up. (PUSH / POP done on Stack Visualization (PUSH / POP) are animated when the Stack page is opened.)

To change the speed, edit the durations (in milliseconds) in the `Anim.play(...)` calls.


## Final configuration
- Stack capacity: **10 batches** (the 11th PUSH shows Stack Overflow).
- Currency: **Pakistani Rupees (PKR / ₨)** throughout the interface and reports.
- All stack actions (PUSH, POP, PEEK, isEmpty, isFull and search) are available on the single **Stack Visualization** page.
- Empty POP shows **Stack Underflow**.
