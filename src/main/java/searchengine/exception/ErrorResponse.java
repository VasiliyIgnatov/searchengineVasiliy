package searchengine.exception;

import lombok.*;
import org.springframework.http.HttpStatus;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private String message;
}
