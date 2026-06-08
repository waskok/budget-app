package pk.ni.pasir_wasko_klaudiusz.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InfoController {

    @GetMapping("/api/info")
    public String info() {
        return "{\"appName\": \"Aplikacja Budżetowa\"," +
                " \"version\": \"1.0\"," +
                " \"message\": \"Witaj w aplikacji budżetowej stworzonej ze Spring Boot!\"}";
    }
}