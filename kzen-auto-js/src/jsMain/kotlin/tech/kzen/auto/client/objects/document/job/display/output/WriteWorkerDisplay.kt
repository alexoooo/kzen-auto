package tech.kzen.auto.client.objects.document.job.display.output

import emotion.react.css
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.details
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.summary
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.objects.document.common.file.fileTableGrid
import tech.kzen.auto.client.objects.document.common.file.fileTableHeaderCell
import tech.kzen.auto.client.objects.document.common.file.fileTableScrollFrame
import tech.kzen.auto.client.objects.document.job.display.ProgressRate
import tech.kzen.auto.client.objects.document.job.display.WorkerDisplayDefault
import tech.kzen.auto.client.objects.document.job.display.WorkerDisplayProps
import tech.kzen.auto.client.objects.document.job.display.WorkerDisplayWrapper
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.auto.common.objects.document.job.JobOutputConventions
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


external interface WriteWorkerDisplayProps: WorkerDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper
    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}


/**
 * The ordinary Worker card, saying in its header what `Write` is writing (the file, by its absolute path, when
 * there is one), how much is on disk and how fast it grows; above its fields each live edit that discarded files it
 * had open, and below them the files, by absolute path.
 */
@Suppress("unused")
class WriteWorkerDisplay(
    props: WriteWorkerDisplayProps
):
    RPureComponent<WriteWorkerDisplayProps, State>(props)
{
    companion object {
        private val noticeColor = Color("#ed6c02")
        private val mutedColor = Color("rgba(0, 0, 0, 0.65)")
    }


    private val byteRate = ProgressRate()


    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        private val attributeViewManager: AttributeViewManager.Wrapper,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        WorkerDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: WorkerDisplayProps.() -> Unit) {
            WriteWorkerDisplay::class.react {
                this.attributeEditorManager = this@Wrapper.attributeEditorManager
                this.attributeViewManager = this@Wrapper.attributeViewManager
                this.clientStateGlobal = this@Wrapper.clientStateGlobal
                this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    override fun ChildrenBuilder.render() {
        WorkerDisplayDefault::class.react {
            this.attributeEditorManager = props.attributeEditorManager
            this.attributeViewManager = props.attributeViewManager
            this.clientStateGlobal = props.clientStateGlobal
            this.mirroredGraphStore = props.mirroredGraphStore
            this.common = props.common
            statusText = { OutputProgressText.write(it, byteRate) }
            bodyBefore = { it.renderDiscards() }
            bodyExtra = { it.renderFiles() }
        }
    }


    // A run of many outputs lists only a few while it goes (the bounded teaser), so the summary says of how many
    private fun ChildrenBuilder.renderFiles() {
        val progress = props.common.progress
        val files = OutputProgressText.writeFiles(progress)
        if (files.isEmpty()) {
            return
        }
        val total = (progress?.longValue(JobOutputConventions.writeFilesKey) ?: 0) +
            (progress?.longValue(JobOutputConventions.writeOpenKey) ?: 0)
        val listed = files.size.toLong()

        details {
            css {
                marginTop = 0.5.em
            }
            summary {
                css {
                    cursor = Cursor.pointer
                    color = mutedColor
                }
                +(if (total > listed) "Files ($listed of ${FormatUtils.decimalSeparator(total)} shown)"
                    else "Files ($listed)")
            }
            div {
                css {
                    fileTableScrollFrame(16.em)
                    marginTop = 0.5.em
                }
                table {
                    css {
                        fileTableGrid()
                        fontSize = 0.85.em
                    }
                    thead {
                        tr {
                            fileTableHeaderCell { +"File" }
                            fileTableHeaderCell { +"Size" }
                            fileTableHeaderCell {}
                        }
                    }
                    tbody {
                        for (file in files) {
                            tr {
                                key = Key(file.path)
                                td {
                                    css {
                                        width = 100.pct
                                        paddingLeft = 0.5.em
                                        fontFamily = FontFamily.monospace
                                        wordBreak = WordBreak.breakAll
                                    }
                                    +file.path
                                }
                                td {
                                    css {
                                        paddingLeft = 0.5.em
                                        paddingRight = 0.5.em
                                        whiteSpace = WhiteSpace.nowrap
                                        color = NamedColor.gray
                                    }
                                    +FormatUtils.readableFileSize(file.bytes)
                                }
                                td {
                                    css {
                                        paddingRight = 0.5.em
                                        whiteSpace = WhiteSpace.nowrap
                                        color = NamedColor.gray
                                    }
                                    if (file.open) {
                                        +"writing"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderDiscards() {
        val notices = OutputProgressText.discards(props.common.progress)
        for ((index, notice) in notices.withIndex()) {
            div {
                key = Key("$index")
                css {
                    marginBottom = 0.5.em
                    fontSize = 0.85.em
                    color = noticeColor
                }
                +notice
            }
        }
    }
}
