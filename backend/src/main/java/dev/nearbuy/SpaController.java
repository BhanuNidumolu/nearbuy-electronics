package dev.nearbuy;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Forwards browser refreshes on frontend routes to the React entry point. */
@Controller
class SpaController {
    @GetMapping({"/my-reservations", "/retailer", "/admin"})
    String forwardFrontendRoutes() {
        return "forward:/index.html";
    }
}
