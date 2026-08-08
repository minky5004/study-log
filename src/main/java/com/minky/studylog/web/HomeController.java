package com.minky.studylog.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/logs";
    }

    /** 로그인 화면은 뷰만 있으면 된다 — 자격증명 처리는 시큐리티 필터가 가져간다. */
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
