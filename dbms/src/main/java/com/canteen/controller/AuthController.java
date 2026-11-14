package com.canteen.controller;

import com.canteen.dao.UserDao;
import com.canteen.model.SessionUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
public class AuthController {
    private final UserDao users;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(UserDao users) {
        this.users = users;
    }

    @GetMapping("/")
    public String home(HttpSession session) {
        SessionUser u = (SessionUser) session.getAttribute("user");
        if (u == null) return "redirect:/login";
        return u.isAdmin() ? "redirect:/admin" : "redirect:/menu";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String password, HttpSession session, Model model) {
        Map<String, Object> u = users.findByEmail(email.trim().toLowerCase());
        if (u == null || !encoder.matches(password, (String) u.get("password_hash"))) {
            model.addAttribute("error", "Wrong email or password. Check both and try again.");
            return "login";
        }
        session.setAttribute("user", new SessionUser(((Number) u.get("user_id")).intValue(),
            (String) u.get("name"), (String) u.get("role")));
        return "redirect:/";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String name, @RequestParam String email, @RequestParam String password,
                           Model model, RedirectAttributes ra) {
        String mail = email.trim().toLowerCase();
        if (name.isBlank() || !mail.contains("@") || password.length() < 6) {
            model.addAttribute("error", "Enter your name, a valid email and a password of at least 6 characters.");
            return "register";
        }
        if (users.findByEmail(mail) != null) {
            model.addAttribute("error", "That email already has an account. Log in instead.");
            return "register";
        }
        users.create(name.trim(), mail, encoder.encode(password), "STUDENT");
        ra.addFlashAttribute("msg", "Account created. Log in to start ordering.");
        return "redirect:/login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
