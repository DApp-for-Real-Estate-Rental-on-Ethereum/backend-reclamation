package ma.fstt.reclamationservice.api.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ReclamationStatsDTO {
    private Long id; // userId
    private Long totalFiled;
    private Long totalReceived;
    private Long pendingFiled; // Filed by user, status OPEN or IN_REVIEW
    private Long resolvedFiled; // Filed by user, status RESOLVED
}
