# Smart Canteen Pre-Ordering System (DBMS Project)

Spring Boot + Thymeleaf website with plain JDBC and MySQL.
Students order ahead, get a daily token, track the order and get a bill. The admin runs the kitchen.

## Setup on Windows

1. **Check Java:** open Command Prompt or the VS Code terminal and run `java -version`.
   (JDK 17 or newer works, including Java 26. The project compiles for Java 17.)
2. **Create the database:** open MySQL Workbench, open `database\schema.sql`, click the lightning bolt.
3. **Set your MySQL login:** edit `src\main\resources\application.properties` and change
   `spring.datasource.username` and `spring.datasource.password`.
4. **Easiest way:** double-click `run.bat` in the `dbms` folder. It asks for your MySQL username and password, creates the database if it does not exist yet (steps 2 and 3 are then not needed), starts the app and opens the website in your browser. Your password is not saved anywhere.

   **Or from the VS Code terminal:** open the `dbms` folder in VS Code, open the terminal (Ctrl + `) and run:

   ```
   mvnw.cmd spring-boot:run
   ```
   The first run downloads Maven and the libraries, so it needs internet and takes a few minutes.
   Wait for `Smart Canteen is running at http://localhost:8080`.
5. If the browser did not open, go to http://localhost:8080 (turn auto-open off with `app.open-browser=false` in `application.properties`).

### Logins (created automatically on first start)
| Role | Email | Password |
|---|---|---|
| Admin | admin@canteen.com | admin123 |
| Student | student1@canteen.com | student123 |
| Student | student2@canteen.com | student123 |
| Student | student3@canteen.com | student123 |

### Troubleshooting
- **PowerShell blocks the command:** use Command Prompt, or run `.\mvnw.cmd spring-boot:run`.
- **Access denied for user 'root':** the password in `application.properties` is wrong.
- **Unknown database 'canteen_db':** run `schema.sql` in Workbench first (step 2).
- **Download of Maven fails:** install Maven yourself and run `mvn spring-boot:run` instead.
- **Port 8080 busy:** change `server.port` in `application.properties`.
- **Java version error:** install JDK 21 and try again.
- **No pickup slots shown for today:** after 17:00 all slots are shown as pre-orders. Change `app.open` / `app.close` to adjust hours.

## Flow
Browser -> Controller (`controller/`) -> DAO with SQL (`dao/`) -> MySQL.
Every query lives in the `dao` folder. Rows come back as maps, so there are no extra model classes.

## DBMS concepts and where they are
| Concept | Where |
|---|---|
| Primary/foreign keys, UNIQUE, CHECK, ENUM, ON DELETE CASCADE | `database/schema.sql` tables |
| 3NF, many-to-many resolved by `order_items` | schema |
| Trigger `trg_reduce_stock` (stock drops when an item is ordered) | schema |
| Trigger `trg_restore_stock` (stock returns when an order is cancelled) | schema |
| Stored procedure `sp_generate_token` (T-001, T-002, restarts daily) | schema, called in `OrderDao.placeOrder` |
| Views `v_kitchen_queue`, `v_daily_sales` | schema, used in `OrderDao.kitchenQueue`, `ReportDao.dailySales` |
| Transaction (lock stock, order, items, token, payment: all or nothing) | `OrderDao.placeOrder` (`@Transactional`, `SELECT ... FOR UPDATE`) |
| Joins, GROUP BY, GROUP_CONCAT, correlated subquery | `OrderDao`, `ReportDao` |
| Password hashing (BCrypt) | `AuthController`, `DataSeeder` |

## ER diagram
In MySQL Workbench: Database -> Reverse Engineer -> pick `canteen_db` -> Next until the diagram appears.

## Features
- Student: register/login, menu with category filter, cart, pickup slot (15 min), payment mode (UPI/Card paid now, Cash paid on collection), token, live status, cancel while "placed", order history, printable bill with 5% GST.
- Admin: kitchen dashboard, order status updates, menu and stock management, daily sales and best sellers.
