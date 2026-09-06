package tech.kzen.auto.server.objects.job.channel


/**
 * The framework-facing side of a [JobChannel] consumer endpoint: a drive loop receives a [ReceivedBatch] — the
 * elements with the channel leases they carry — and hands each element's hold on to its callback, where the SPI
 * `receiveBatch` releases a raw reader's previous batch on the next pull instead. Not part of the Worker SPI.
 */
internal interface FrameworkChannelInput {
    suspend fun receiveFrameworkBatch(): ReceivedBatch?
}
