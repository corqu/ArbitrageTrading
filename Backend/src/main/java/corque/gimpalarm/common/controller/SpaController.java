package corque.gimpalarm.common.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping(value = {"/mypage", "/arbitrage", "/auth"})
    public String forward() {
        return "forward:/index.html";
    }
}
