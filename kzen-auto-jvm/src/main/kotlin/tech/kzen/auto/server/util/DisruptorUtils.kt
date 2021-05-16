package tech.kzen.auto.server.util

import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.LiteBlockingWaitStrategy
import com.lmax.disruptor.WaitStrategy
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.EventHandlerGroup


object DisruptorUtils {
    fun newWaitStrategy(): WaitStrategy {
//        return BusySpinWaitStrategy()
//        return YieldingWaitStrategy()
//        return SleepingWaitStrategy()
//        return BlockingWaitStrategy()
        return LiteBlockingWaitStrategy()
    }



    fun <T> addHandler(
        disruptor: Disruptor<T>,
        group: EventHandlerGroup<T>?,
        handler: EventHandler<T>
    ): EventHandlerGroup<T> {
        return when (group) {
            null ->
                disruptor.handleEventsWith(handler)

            else ->
                group.handleEventsWith(handler)
        }
    }
}