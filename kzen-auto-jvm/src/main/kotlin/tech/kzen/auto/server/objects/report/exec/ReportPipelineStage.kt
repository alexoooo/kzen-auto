package tech.kzen.auto.server.objects.report.exec

import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.LifecycleAware
import java.util.concurrent.atomic.AtomicInteger


// https://groups.google.com/g/lmax-disruptor/c/C5dACGttjNc
abstract class ReportPipelineStage<T>(
    private val name: String
):
    EventHandler<T>,
    LifecycleAware
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val nextThreadNumber = AtomicInteger()

        fun nextNumber(): Int {
            return nextThreadNumber.getAndIncrement()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var oldName: String? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun onStart() {
        val currentThread = Thread.currentThread()
        oldName = currentThread.name

        val number = nextNumber()
        currentThread.name = "stage_${name}_$number"
    }


    override fun onShutdown() {
        Thread.currentThread().name = oldName
    }
}