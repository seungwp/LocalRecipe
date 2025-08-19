package com.example.ocrproject.controller;

import com.example.ocrproject.entity.User;
import com.example.ocrproject.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {
    @GetMapping("/")
    public String home() {
    return "redirect:/login.html";
    }


    @Autowired
    private UserRepository userRepository;

    @PostMapping("/signup")
    public String signup(@ModelAttribute User req) {
        if (userRepository.findByUsername(req.getUsername()) != null) {
            return "redirect:/signup.html?error=exist";
        }
        userRepository.save(req);
        return "redirect:/login.html?success";
    }

    @PostMapping("/login")
    public String login(@ModelAttribute User req, HttpSession session) {
        User user = userRepository.findByUsername(req.getUsername());
        if (user != null && user.getPassword().equals(req.getPassword())) {
            session.setAttribute("user", user.getId());
            return "redirect:/main.html";
        } else {
            return "redirect:/login.html?error";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login.html";
    }
    
}