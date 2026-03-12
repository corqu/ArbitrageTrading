package corque.gimpalarm.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserCredentialRequestDto {
    private Long userId;
    private String exchange;
    private String accessKey;
    private String secretKey;
}
