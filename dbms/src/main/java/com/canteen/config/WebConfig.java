package com.canteen.config;

import com.canteen.model.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Login check: every page except login/register needs a session; /admin/** needs the ADMIN role. */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
                res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
                res.setHeader("Pragma", "no-cache");
                res.setDateHeader("Expires", 0);
                return true;
            }
        }).addPathPatterns("/**").excludePathPatterns("/css/**");
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
                HttpSession session = req.getSession(false);
                SessionUser user = session == null ? null : (SessionUser) session.getAttribute("user");
                if (user == null) {
                    res.sendRedirect("/login");
                    return false;
                }
                if (req.getRequestURI().startsWith("/admin") && !user.isAdmin()) {
                    res.sendRedirect("/menu");
                    return false;
                }
                return true;
            }
        }).addPathPatterns("/**")
          .excludePathPatterns("/", "/login", "/register", "/css/**", "/error");
    }
}
