package tech.kzen.auto.server.objects.script.step.eval


interface StepExpression {
    fun evaluate(predecessorValues: List<Any?>): Any?
}