package com.omnichannel.channels;

/**
 * Contrato que todo canal implementa (padrão Adapter —
 * docs/03-arquitetura.md): novo canal = nova implementação deste contrato,
 * nenhum outro módulo conhece detalhes de provedor.
 *
 * <p>O caminho de ENTRADA não passa por esta interface: cada adapter expõe
 * seu webhook próprio, autentica o provedor (assinatura), normaliza o
 * payload para {@link InboundMessageReceived} e entrega ao
 * {@link InboundMessageGateway}.
 */
public interface ChannelAdapter {

	ChannelType type();

	/**
	 * Envia uma mensagem normalizada pelo canal. Implementações devem lançar
	 * {@link ChannelDeliveryException} em falha de entrega — o chamador decide
	 * retry/DLQ via fila, nunca o adapter silencia o erro.
	 */
	void send(OutboundMessage message, ChannelCredentials credentials);
}
