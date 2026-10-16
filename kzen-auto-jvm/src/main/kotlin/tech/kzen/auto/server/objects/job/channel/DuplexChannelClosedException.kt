package tech.kzen.auto.server.objects.job.channel


/** A [DuplexJobChannel] request that no server can answer any more: every client endpoint has closed. */
class DuplexChannelClosedException: IllegalStateException("Duplex channel closed before the request was answered")
