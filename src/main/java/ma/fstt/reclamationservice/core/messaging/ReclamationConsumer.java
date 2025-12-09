package ma.fstt.reclamationservice.core.messaging;

import lombok.RequiredArgsConstructor;
import ma.fstt.reclamationservice.core.service.ReclamationService;
import ma.fstt.reclamationservice.domain.entity.Reclamation;
import ma.fstt.reclamationservice.domain.entity.ReclamationStatus;
import ma.fstt.reclamationservice.domain.entity.ReclamationType;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReclamationConsumer {

    private final ReclamationService reclamationService;

    @RabbitListener(queues = "reclamation")
    public void handleReclamation(Map<String, Object> message) {
        try {
            Long bookingId = Long.valueOf(message.get("bookingId").toString());
            Long userId = Long.valueOf(message.get("userId").toString());
            String roleStr = message.get("complainantRole").toString();
            String reclamationTypeStr = message.get("reclamationType").toString();
            String title = message.getOrDefault("title", "").toString();
            String description = message.getOrDefault("description", "").toString();

            Reclamation.ComplainantRole role = Reclamation.ComplainantRole.valueOf(roleStr);
            ReclamationType type = ReclamationType.valueOf(reclamationTypeStr);

            reclamationService.createReclamation(bookingId, userId, role, type, title, description);
        } catch (Exception e) {
            throw e;
        }
    }
}

