//package tech.kzen.auto.client.api
//
//
//import tech.kzen.lib.common.notation.model.ProjectPath
//import kotlin.js.Json
////import kotlinx.serialization.json.JSON as KJSON
//
//class RestApi(private val baseUrl: String, private val baseWsUrl: String) {
//    suspend fun getNotation(path: ProjectPath): String =
//            httpGet("$baseUrl/notation/${path.relativeLocation}")
//
//    suspend fun scan(): List<ProjectPath> {
//        val scanText = httpGet("$baseUrl/scan")
////        println("scanText: $scanText")
//
//        val builder = mutableListOf<ProjectPath>()
//
//        JSON.parse<Array<Json>>(scanText)
//                .map { it["relativeLocation"] as String }
//                .mapTo(builder) { ProjectPath(it) }
//
//        return builder
//
////        val parsed = JSON.parse<List<ProjectPath>>(scanText)
////        console.log("parsed", parsed)
//
//        //val parsed: List<ProjectPath> = JSON.parse(scanText)
//        //println("parsed: $parsed")
////        return parsed
//
////        return listOf(
////                ProjectPath("notation.yaml"))
//    }
//}
//
