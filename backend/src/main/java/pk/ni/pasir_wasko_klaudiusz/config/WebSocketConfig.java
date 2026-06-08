package pk.ni.pasir_wasko_klaudiusz.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import pk.ni.pasir_wasko_klaudiusz.service.GroupTransactionService;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final GroupTransactionService groupTransactionService;

    public WebSocketConfig(GroupTransactionService groupTransactionService) {
        this.groupTransactionService = groupTransactionService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(groupTransactionService, "/ws/group-notifications")
                .setAllowedOrigins("*");
    }
}