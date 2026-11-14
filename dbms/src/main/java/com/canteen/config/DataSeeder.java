package com.canteen.config;

import com.canteen.dao.UserDao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** On first start (empty users table) creates the demo admin and students with BCrypt-hashed passwords. */
@Component
public class DataSeeder implements CommandLineRunner {
    private final UserDao users;

    @Value("${app.open-browser:true}")
    private boolean openBrowser;

    public DataSeeder(UserDao users) {
        this.users = users;
    }

    @Override
    public void run(String... args) {
        if (users.count() == 0) {
            BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
            users.create("Canteen Admin", "Sakshi@gmail.com", enc.encode("admin123"), "ADMIN");
            users.create("Ruhani", "ruhani@gmail.com", enc.encode("student123"), "STUDENT");
            users.create("Vaishnavi", "vaishnavi@gmail.com", enc.encode("student123"), "STUDENT");
            users.create("Shivantika", "shivantika@gmail.com", enc.encode("student123"), "STUDENT");
            System.out.println(">>> Demo users created (see README for logins)");
        }
        String url = "http://localhost:8080";
        System.out.println(">>> Smart Canteen is running at " + url);
        if (openBrowser && System.getProperty("os.name", "").toLowerCase().contains("win")) {
            try {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();   // opens the default browser
            } catch (Exception e) {
                System.out.println(">>> Open " + url + " in your browser.");
            }
        }
    }
}
