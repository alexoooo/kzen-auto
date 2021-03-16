package tech.kzen.auto.server.util

import com.lmax.disruptor.WaitStrategy
import com.lmax.disruptor.YieldingWaitStrategy


object DisruptorUtils {
    fun newWaitStrategy(): WaitStrategy {
//        return BusySpinWaitStrategy()
        return YieldingWaitStrategy()
//        return LiteBlockingWaitStrategy()
    }
}