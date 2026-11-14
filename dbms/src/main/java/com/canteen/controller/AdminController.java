package com.canteen.controller;

import com.canteen.dao.MenuDao;
import com.canteen.dao.OrderDao;
import com.canteen.dao.ReportDao;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
public class AdminController {
    private final MenuDao menu;
    private final OrderDao orders;
    private final ReportDao reports;

    public AdminController(MenuDao menu, OrderDao orders, ReportDao reports) {
        this.menu = menu;
        this.orders = orders;
        this.reports = reports;
    }

    @GetMapping("/admin")
    public String dashboard(Model m) {
        m.addAttribute("stats", reports.stats());
        m.addAttribute("queue", orders.kitchenQueue());
        m.addAttribute("top", reports.topItems());
        return "admin/dashboard";
    }

    @GetMapping("/admin/menu")
    public String menuPage(Model m) {
        m.addAttribute("items", menu.all());
        m.addAttribute("categories", menu.categories());
        return "admin/menu";
    }

    @PostMapping("/admin/menu/add")
    public String add(@RequestParam String name, @RequestParam(defaultValue = "") String description,
                      @RequestParam BigDecimal price, @RequestParam int categoryId,
                      @RequestParam(defaultValue = "🍽️") String emoji, @RequestParam int stock,
                      RedirectAttributes ra) {
        if (name.isBlank() || price.signum() <= 0 || stock < 0) {
            ra.addFlashAttribute("error", "Enter a name, a price above 0 and a stock of 0 or more.");
        } else {
            menu.add(name.trim(), description.trim(), price, categoryId, emoji.isBlank() ? "🍽️" : emoji.trim(), stock);
            ra.addFlashAttribute("msg", name.trim() + " added to the menu.");
        }
        return "redirect:/admin/menu";
    }

    @PostMapping("/admin/menu/update")
    public String update(@RequestParam int itemId, @RequestParam BigDecimal price, @RequestParam int stock,
                         @RequestParam(required = false) String available, RedirectAttributes ra) {
        if (price.signum() <= 0 || stock < 0) {
            ra.addFlashAttribute("error", "Price must be above 0 and stock can't be negative.");
        } else {
            try {
                menu.update(itemId, price, stock, available != null);
                ra.addFlashAttribute("msg", "Item updated.");
            } catch (DataAccessException e) {
                ra.addFlashAttribute("error", "Couldn't update that item.");
            }
        }
        return "redirect:/admin/menu";
    }

    @GetMapping("/admin/orders")
    public String ordersPage(Model m) {
        m.addAttribute("orders", orders.allForAdmin());
        return "admin/orders";
    }

    @PostMapping("/admin/orders/status")
    public String status(@RequestParam int orderId, @RequestParam String status, RedirectAttributes ra) {
        if (List.of("PREPARING", "READY", "COLLECTED", "CANCELLED").contains(status)) {
            orders.setStatus(orderId, status);
            ra.addFlashAttribute("msg", "Order #" + orderId + " is now " + status.toLowerCase() + ".");
        }
        return "redirect:/admin/orders";
    }

    @GetMapping("/admin/sales")
    public String sales(Model m) {
        m.addAttribute("daily", reports.dailySales());
        m.addAttribute("top", reports.topItems());
        return "admin/sales";
    }
}
