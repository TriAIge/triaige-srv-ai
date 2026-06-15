package br.com.triaige.aiservice.application.port.out;

public interface DocumentStorageGateway {
    /**
     * Busca o conteúdo de texto normalizado de um documento no armazenamento de objetos.
     * Nunca retorna bytes brutos de PDF.
     */
    String getText(String bucket, String key);
}
