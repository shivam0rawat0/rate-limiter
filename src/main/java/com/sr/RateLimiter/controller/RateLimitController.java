package com.sr.RateLimiter.controller;

import com.sr.RateLimiter.cache.InMemoryDB;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gateway")
public class RateLimitController {

    @Autowired
    InMemoryDB cache;

    @GetMapping("/")
    public String GET(HttpServletRequest request){
        String ip = request.getHeader("X-FORWARDED-FOR");
        if (ip == null) ip = request.getRemoteAddr();
        if(cache.isValid(ip))
            return "200";
        return "405";
    }
}
