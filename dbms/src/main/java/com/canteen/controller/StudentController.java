package com.canteen.controller;

import com.canteen.dao.MenuDao;
import com.canteen.dao.OrderDao;
import com.canteen.model.SessionUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
public class StudentController {
    private static final List<String> STEPS = List.of("PLACED", "PREPARING", "READY", "COLLECTED");

    private final MenuDao menu;
    private final OrderDao orders;

    @Value("${app.gst.rate}") private BigDecimal gstRate;
    @Value("${app.open}") private String open;
    @Value("${app.close}") private String close;

    public StudentController(MenuDao menu, OrderDao orders) {
        this.menu = menu;
        this.orders = orders;
    }

    private SessionUser user(HttpSession s) {
        return (SessionUser) s.getAttribute("user");
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Integer> cart(HttpSession s) {
        Map<Integer, Integer> c = (Map<Integer, Integer>) s.getAttribute("cart");
        if (c == null) {
            c = new LinkedHashMap<>();
            s.setAttribute("cart", c);
        }
        return c;
    }

    // ---------------- MENU ----------------
    @GetMapping("/menu")
    public String menuPage(@RequestParam(required = false) Integer cat, Model m) {
        m.addAttribute("categories", menu.categories());
        m.addAttribute("items", menu.available(cat));
        m.addAttribute("cat", cat);
        return "menu";
    }

    // ---------------- CART ----------------
    @PostMapping("/cart/add")
    public String add(@RequestParam int itemId, @RequestParam(defaultValue = "1") int qty,
                      @RequestParam(required = false) Integer cat, HttpSession s, RedirectAttributes ra) {
        String back = "redirect:/menu" + (cat == null ? "" : "?cat=" + cat);
        Map<String, Object> item = menu.findById(itemId);
        if (item == null || ((Number) item.get("can_order")).intValue() != 1) {
            ra.addFlashAttribute("error", "That item is sold out right now.");
            return back;
        }
        Map<Integer, Integer> c = cart(s);
        int want = c.getOrDefault(itemId, 0) + Math.max(1, qty);
        int stock = ((Number) item.get("stock")).intValue();
        if (want > stock) {
            ra.addFlashAttribute("error", "Only " + stock + " " + item.get("name") + " left, and you already have "
                + c.getOrDefault(itemId, 0) + " in your cart.");
            return back;
        }
        c.put(itemId, want);
        ra.addFlashAttribute("msg", item.get("name") + " added to your cart.");
        return back;
    }

    @GetMapping("/cart")
    public String cartPage(HttpSession s, Model m) {
        fillCart(s, m);
        return "cart";
    }

    @PostMapping("/cart/update")
    public String update(@RequestParam int itemId, @RequestParam int qty, HttpSession s, RedirectAttributes ra) {
        Map<Integer, Integer> c = cart(s);
        Map<String, Object> item = menu.findById(itemId);
        if (qty <= 0 || item == null) {
            c.remove(itemId);
        } else if (qty > ((Number) item.get("stock")).intValue()) {
            ra.addFlashAttribute("error", "Only " + item.get("stock") + " " + item.get("name") + " left.");
        } else {
            c.put(itemId, qty);
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String remove(@RequestParam int itemId, HttpSession s) {
        cart(s).remove(itemId);
        return "redirect:/cart";
    }

    private void fillCart(HttpSession s, Model m) {
        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        Iterator<Map.Entry<Integer, Integer>> it = cart(s).entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> e = it.next();
            Map<String, Object> item = menu.findById(e.getKey());
            if (item == null) { it.remove(); continue; }
            Map<String, Object> line = new HashMap<>(item);
            BigDecimal lineTotal = ((BigDecimal) item.get("price")).multiply(BigDecimal.valueOf(e.getValue()));
            line.put("qty", e.getValue());
            line.put("line_total", lineTotal);
            lines.add(line);
            subtotal = subtotal.add(lineTotal);
        }
        BigDecimal tax = subtotal.multiply(gstRate).setScale(2, RoundingMode.HALF_UP);
        m.addAttribute("lines", lines);
        m.addAttribute("subtotal", subtotal);
        m.addAttribute("tax", tax);
        m.addAttribute("total", subtotal.add(tax));
        m.addAttribute("gstPercent", gstRate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString());
    }

    // ---------------- CHECKOUT ----------------
    private List<String> slots() {
        LocalTime start = LocalTime.parse(open), end = LocalTime.parse(close);
        LocalTime earliest = LocalTime.now().plusMinutes(10);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("HH:mm");
        List<String> all = new ArrayList<>(), later = new ArrayList<>();
        for (LocalTime t = start; !t.isAfter(end); t = t.plusMinutes(15)) {
            all.add(t.format(f));
            if (t.isAfter(earliest)) later.add(t.format(f));
        }
        return later.isEmpty() ? all : later;   // after closing time, show all slots (next-day pre-order)
    }

    @GetMapping("/checkout")
    public String checkoutPage(HttpSession s, Model m) {
        if (cart(s).isEmpty()) return "redirect:/cart";
        fillCart(s, m);
        m.addAttribute("slots", slots());
        return "checkout";
    }

    @PostMapping("/checkout")
    public String place(@RequestParam String pickup, @RequestParam String mode, HttpSession s, RedirectAttributes ra) {
        Map<Integer, Integer> c = cart(s);
        if (c.isEmpty()) return "redirect:/cart";
        if (!slots().contains(pickup) || !List.of("CASH", "UPI", "CARD").contains(mode)) {
            ra.addFlashAttribute("error", "Choose a pickup time and a payment mode.");
            return "redirect:/checkout";
        }
        try {
            int id = orders.placeOrder(user(s).getUserId(), new LinkedHashMap<>(c), pickup, mode, gstRate);
            c.clear();
            return "redirect:/order/" + id;
        } catch (IllegalStateException | DataAccessException e) {
                        e.printStackTrace();
            ra.addFlashAttribute("error", e instanceof IllegalStateException
                ? e.getMessage() : "Some items just ran out of stock. Review your cart and try again.");
            return "redirect:/cart";
        }
    }

    // ---------------- ORDERS ----------------
    private Map<String, Object> owned(int id, HttpSession s) {
        Map<String, Object> o = orders.detail(id);
        SessionUser u = user(s);
        if (o == null || (!u.isAdmin() && ((Number) o.get("user_id")).intValue() != u.getUserId())) return null;
        return o;
    }

    @GetMapping("/order/{id}")
    public String orderPage(@PathVariable int id, HttpSession s, Model m) {
        Map<String, Object> o = owned(id, s);
        if (o == null) return "redirect:/orders";
        String status = (String) o.get("status");
        m.addAttribute("order", o);
        m.addAttribute("items", orders.items(id));
        m.addAttribute("steps", List.of("Placed", "Preparing", "Ready", "Collected"));
        m.addAttribute("stepIdx", STEPS.indexOf(status));
        m.addAttribute("active", !status.equals("COLLECTED") && !status.equals("CANCELLED"));
        return "order";
    }

    @PostMapping("/order/{id}/cancel")
    public String cancel(@PathVariable int id, HttpSession s, RedirectAttributes ra) {
        Map<String, Object> o = owned(id, s);
        if (o != null && "PLACED".equals(o.get("status"))) {
            orders.setStatus(id, "CANCELLED");
            ra.addFlashAttribute("msg", "Order cancelled. Any online payment will be refunded.");
        } else {
            ra.addFlashAttribute("error", "This order is already being prepared and can't be cancelled.");
        }
        return "redirect:/order/" + id;
    }

    @GetMapping("/orders")
    public String history(HttpSession s, Model m) {
        m.addAttribute("orders", orders.byUser(user(s).getUserId()));
        return "orders";
    }

    @GetMapping("/bill/{id}")
    public String bill(@PathVariable int id, HttpSession s, Model m) {
        Map<String, Object> o = owned(id, s);
        if (o == null) return "redirect:/orders";
        m.addAttribute("order", o);
        m.addAttribute("items", orders.items(id));
        m.addAttribute("gstPercent", gstRate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString());
        return "bill";
    }
}
