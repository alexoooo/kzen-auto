package tech.kzen.auto.server.codegen


object KzenAutoAllCodegen {
    @JvmStatic
    fun main(args: Array<String>) {
        KzenAutoCommonCodegen.main(args)
        KzenAutoJsCodegen.main(args)
        KzenAutoJvmCodegen.main(args)
    }
}