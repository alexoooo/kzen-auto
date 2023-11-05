package tech.kzen.auto.server.util

import com.lmax.disruptor.BlockingWaitStrategy
import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.WaitStrategy
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.EventHandlerGroup


object DisruptorUtils {
    fun newWaitStrategy(): WaitStrategy {
//        return BusySpinWaitStrategy()
//        return YieldingWaitStrategy()
//        return SleepingWaitStrategy()
        return BlockingWaitStrategy()
//        return LiteBlockingWaitStrategy()
    }



    fun <T> addHandlers(
        disruptor: Disruptor<T>,
        group: EventHandlerGroup<T>?,
        vararg handlers: EventHandler<T>
    ): EventHandlerGroup<T> {
        return when (group) {
            null ->
                disruptor.handleEventsWith(*handlers)

            else ->
                group.handleEventsWith(*handlers)
        }
    }
}