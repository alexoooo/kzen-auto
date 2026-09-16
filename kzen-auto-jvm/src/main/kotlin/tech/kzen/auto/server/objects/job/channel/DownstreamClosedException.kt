package tech.kzen.auto.server.objects.job.channel


/**
 * The consumer side of a channel has finished and will receive nothing more (content streaming spike CS2,
 * docs/plans/2026-09-16_borrowed-elements.md). Thrown to a producer from [JobChannel]'s `send` / `flush`
 * once the consumer [closed its end][FrameworkChannelInput.closeConsumer] — including a producer already parked
 * on a full channel, which is resumed with it — and thrown by a scope body Worker that completes early (a
 * `Take` inside an entry scope) so the scope stops reading. It is the ONE signal that separates a downstream
 * that is CLOSED from one that is merely PAUSED (a paused consumer leaves the producer parked, indefinitely, in
 * ordinary backpressure): a framework drive loop that catches it stops producing, skips its completion emit
 * and closes its own input in turn, so the closure propagates upstream one Worker at a time.
 *
 * Not a failure: a Worker ending on it settles normally with its output closed.
 */
class DownstreamClosedException(
    message: String
): RuntimeException(message)
