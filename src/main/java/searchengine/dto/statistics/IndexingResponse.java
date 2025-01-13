package searchengine.dto.statistics;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class IndexingResponse {
    private boolean result;
    private String error;
}
