package io.taanielo.jmud.core.bank.repository.json;

/**
 * JSON transfer object for a bank definition file ({@code bank.*.json}).
 */
record BankDto(
    int schemaVersion,
    String id,
    String name,
    String roomId
) {
}
