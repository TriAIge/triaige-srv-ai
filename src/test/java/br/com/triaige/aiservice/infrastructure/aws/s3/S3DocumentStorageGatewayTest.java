package br.com.triaige.aiservice.infrastructure.aws.s3;

import br.com.triaige.aiservice.domain.exception.AiProviderException;
import br.com.triaige.aiservice.domain.exception.DocumentNotFoundException;
import br.com.triaige.aiservice.domain.model.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3DocumentStorageGateway")
class S3DocumentStorageGatewayTest {

    @Mock private S3Client s3Client;

    private S3DocumentStorageGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new S3DocumentStorageGateway(s3Client);
    }

    @Test
    @DisplayName("deve retornar texto quando arquivo existe no S3")
    void shouldReturnTextWhenFileExists() {
        // Arrange
        String expectedContent = "Texto jurídico normalizado do documento";
        byte[] bytes = expectedContent.getBytes(StandardCharsets.UTF_8);

        ResponseInputStream<GetObjectResponse> responseStream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(bytes)));

        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        // Act
        String result = gateway.getText("my-bucket", "path/to/doc.txt");

        // Assert
        assertThat(result).isEqualTo(expectedContent);
    }

    @Test
    @DisplayName("deve lançar DocumentNotFoundException quando chave não existe")
    void shouldThrowDocumentNotFoundWhenKeyMissing() {
        // Arrange
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Key not found").build());

        // Act & Assert
        assertThatThrownBy(() -> gateway.getText("my-bucket", "missing/key.txt"))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining("my-bucket")
                .hasMessageContaining("missing/key.txt");
    }

    @Test
    @DisplayName("deve lançar AiProviderException com S3_ACCESS_DENIED em 403")
    void shouldThrowAccessDeniedOn403() {
        // Arrange
        S3Exception forbidden = (S3Exception) S3Exception.builder()
                .statusCode(403)
                .message("Access Denied")
                .build();
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(forbidden);

        // Act & Assert
        assertThatThrownBy(() -> gateway.getText("my-bucket", "protected/key.txt"))
                .isInstanceOf(AiProviderException.class)
                .satisfies(e -> assertThat(((AiProviderException) e).getErrorCode())
                        .isEqualTo(ErrorCode.S3_ACCESS_DENIED));
    }
}
