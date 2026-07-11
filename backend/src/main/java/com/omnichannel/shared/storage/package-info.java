/**
 * API de object storage exposta aos demais módulos: gravação de mídia e
 * URLs pré-assinadas de leitura (S3-compatível: Cloudflare R2 em produção,
 * MinIO em dev/teste — docs/04-stack-tecnologica.md).
 */
@org.springframework.modulith.NamedInterface("storage")
package com.omnichannel.shared.storage;
