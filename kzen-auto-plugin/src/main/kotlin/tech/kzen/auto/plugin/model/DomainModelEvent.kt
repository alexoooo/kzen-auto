package tech.kzen.auto.plugin.model
//
//
//// TODO: use to avoid indirection in calculated column expressions?
//interface DomainModelEvent<T> {
////    companion object {
////        fun <T: Any> wrap(value: T): DomainModelEvent<T> {
////            @Suppress("UNCHECKED_CAST")
////            return object : DomainModelEvent<T> {
////                override fun underlyingType(): Class<T> {
////                    return value.javaClass
////                }
////
////                override fun underlyingValue(): T {
////                    return value
////                }
////            }
////        }
////    }
//
//    fun underlyingType(): Class<T>
//    fun underlyingValue(): T
//}