package com.example.webrtcnm.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    // Maps the root URL to index.html
    @GetMapping("/")
    public String showIndexPage() {
        return "index"; // This will resolve to templates/index.html
    }

}