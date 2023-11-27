package tech.kzen.auto.server.objects.sequence.step.eval


interface StepExpression {
    fun evaluate(predecessorValues: List<Any?>): Any?
}