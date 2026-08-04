package com.minky.studylog.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /** 목록 화면(Task 5)이 생기면 redirect:/logs 로 바꾼다. */
    @GetMapping("/")
    public String home() {
        return "home";
    }
}
