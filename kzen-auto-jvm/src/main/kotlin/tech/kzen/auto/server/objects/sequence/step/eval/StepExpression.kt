package tech.kzen.auto.server.objects.sequence.step.eval


interface StepExpression {
    fun evaluate(): Any?
}