/**
 * Copyright (c) Yuki Ono.
 * Licensed under the MIT License.
 */

package app

import com.beust.klaxon.Klaxon
import java.io.*
import java.math.BigDecimal
import java.net.InetAddress
import java.net.Socket
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val VERSION = BigDecimal("1.0")
const val CRLF = "\r\n"

fun findLfIndex(fileBuf: ByteArray, offset: Int): Int {
    var i = offset
    while (i < fileBuf.size) {
        if (fileBuf[i] == '\n'.toByte())
            return i
        i += 1
    }
    return -1
}

fun findAllLfIndices(fileBuf: ByteArray): List<Int> {
    val indices = mutableListOf<Int>()
    var i = 0
    while (true) {
        i = findLfIndex(fileBuf, i)
        if (i == -1)
            return indices

        indices.add(i)
        i += 1
    }
}

fun copyNew(srcBuf: ByteArray, offset: Int, endIndex: Int): ByteArray {
    val destBuf = ByteArray(endIndex - offset)
    srcBuf.copyInto(destBuf, 0, offset, endIndex)
    return destBuf
}

fun getRawLines(fileBuf: ByteArray): List<ByteArray> {
    var offset = 0
    return findAllLfIndices(fileBuf).plus(fileBuf.size - 1).map {
        val line = copyNew(fileBuf, offset, it + 1)
        offset = it + 1
        return@map line
    }
}

val DATE_BYTES = "Date:".toByteArray(Charsets.UTF_8)
val MESSAGE_ID_BYTES = "Message-ID:".toByteArray(Charsets.UTF_8)

fun matchHeaderField(line: ByteArray, header: ByteArray): Boolean {
    if (line.size < header.size)
        return false

    var i = 0
    while (i < header.size) {
        if (header[i] != line[i])
            return false
        i += 1
    }
    return true
}

fun isDateLine(line: ByteArray): Boolean {
    return matchHeaderField(line, DATE_BYTES)
}

fun makeNowDateLine(): String {
    val time = OffsetDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
    return "Date: " + time.format(formatter) + CRLF
}

fun isMessageIdLine(line: ByteArray): Boolean {
    return matchHeaderField(line, MESSAGE_ID_BYTES)
}

fun makeRandomMessageIdLine(): String {
    val chars =  ('a'..'z') + ('A'..'Z') + ('0'..'9')
    val length = 62
    return "Message-ID: <${(1..length).map { chars.random() }.joinToString("")}>" + CRLF
}

private fun findLineIndex(lines: List<ByteArray>, linePred: (ByteArray) -> Boolean): Int {
    return lines.indexOfFirst(linePred)
}

fun findDateLineIndex(lines: List<ByteArray>): Int {
    return findLineIndex(lines, ::isDateLine)
}

fun findMessageIdLineIndex(lines: List<ByteArray>): Int {
    return findLineIndex(lines, ::isMessageIdLine)
}

private fun replaceLine(lines: MutableList<ByteArray>, update: Boolean, find_line: (List<ByteArray>) -> Int, make_line: () -> String): Unit {
    if (update) {
        val idx = find_line(lines)
        if (idx != -1)
            lines[idx] = make_line().toByteArray(Charsets.UTF_8)
    }
}

fun replaceRawLines(lines: List<ByteArray>, updateDate: Boolean, updateMessageId: Boolean): List<ByteArray> {
    if (!updateDate && !updateMessageId)
        return lines

    val repsLines = lines.toMutableList()

    replaceLine(repsLines, updateDate, ::findDateLineIndex, ::makeNowDateLine)
    replaceLine(repsLines, updateMessageId, ::findMessageIdLineIndex, ::makeRandomMessageIdLine)

    return repsLines
}

fun concatRawLines(lines: List<ByteArray>): ByteArray {
    val buf = ByteArray(lines.sumBy { it.size })
    var offset = 0
    for (l in lines) {
        l.copyInto(buf, offset, 0, l.size)
        offset += l.size
    }
    return buf
}

fun replaceRawBytes(fileBuf: ByteArray, updateDate: Boolean, updateMessageId: Boolean): ByteArray {
    return concatRawLines(replaceRawLines(getRawLines(fileBuf), updateDate, updateMessageId))
}

@Volatile var useParallel = false

fun getCurrentIdPrefix(): String {
    return if (useParallel) "id: ${Thread.currentThread().id}, " else ""
}

fun sendRawBytes(output: OutputStream, file: String, updateDate: Boolean, updateMessageId: Boolean): Unit {
    val fileObj = File(file)
    println(getCurrentIdPrefix() + "send: ${fileObj.absolutePath}")

    val buf = replaceRawBytes(fileObj.readBytes(), updateDate, updateMessageId)
    output.write(buf)
    output.flush()
}

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

data class Settings (
    val smtpHost: String,
    val smtpPort: Int,
    val fromAddress: String,
    val toAddress: List<String>,
    val emlFile: List<String>,
    val updateDate: Boolean,
    val updateMessageId: Boolean,
    val useParallel: Boolean
)

fun getSettings(file: String): Settings? {
    return Klaxon().parse<Settings>(File(file).readText())
}

val RECV_REGEX = Regex("""^\d{3} .+""")

fun isSuccess(line: String): Boolean {
    return arrayOf('2', '3').contains(line.firstOrNull() ?: '0')
}

fun recvLine(reader: BufferedReader): String {
    while (true) {
        val line = reader.readLine()?.trim() ?: throw IOException("Connection closed by foreign host.")
        println(getCurrentIdPrefix() + "recv: $line")

        if (RECV_REGEX.containsMatchIn(line)) {
            if (!isSuccess(line))
                throw IOException(line)

            return line
        }
    }
}

fun sendLine(writer: BufferedWriter, cmd: String): Unit {
    println(getCurrentIdPrefix() + "send: " + if (cmd == "$CRLF.") "<CRLF>." else cmd)

    writer.write(cmd)
    writer.write(CRLF)
    writer.flush()
}

typealias SendCmd = (String) -> String

fun makeSendCmd(reader: BufferedReader, writer: BufferedWriter): SendCmd {
    return { cmd ->
        sendLine(writer, cmd)
        recvLine(reader)
    }
}

fun sendHello(send: SendCmd): Unit {
    send("EHLO localhost")
}

fun sendFrom(send: SendCmd, fromAddr: String): Unit {
    send("MAIL FROM: <$fromAddr>")
}

fun sendRcptTo(send: SendCmd, toAddrs: List<String>): Unit {
    for (addr in toAddrs)
        send("RCPT TO: <$addr>")
}

fun sendData(send: SendCmd): Unit {
    send("DATA")
}

fun sendCrLfDot(send: SendCmd): Unit {
    send("$CRLF.")
}

fun sendQuit(send: SendCmd): Unit {
    send("QUIT")
}

fun sendRset(send: SendCmd): Unit {
    send("RSET")
}

fun sendMessages(settings: Settings, emlFiles: List<String>): Unit {
    val addr = InetAddress.getByName(settings.smtpHost)
    Socket(addr, settings.smtpPort).use { socket ->
        socket.soTimeout = 1000

        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        val bufWriter = BufferedWriter(OutputStreamWriter(output))
        val bufReader = BufferedReader(InputStreamReader(input))

        val send = makeSendCmd(bufReader, bufWriter)

        recvLine(bufReader)
        sendHello(send)

        var mailSent = false
        for (file in emlFiles) {
            if (!File(file).exists()) {
                println("$file: EML file does not exists")
                continue
            }

            if (mailSent) {
                println("---")
                sendRset(send)
            }

            sendFrom(send, settings.fromAddress)
            sendRcptTo(send, settings.toAddress)
            sendData(send)
            sendRawBytes(output, file, settings.updateDate, settings.updateMessageId)
            sendCrLfDot(send)
            mailSent = true
        }

        sendQuit(send)
    }
}

fun sendOneMessage(settings: Settings, file: String) {
    sendMessages(settings, listOf(file))
}

fun makeJsonSample(): String {
    return """{
    "smtpHost": "172.16.3.151",
    "smtpPort": 25,
    "fromAddress": "a001@ah62.example.jp",
    "toAddress": [
        "a001@ah62.example.jp",
        "a002@ah62.example.jp",
        "a003@ah62.example.jp"
    ],
    "emlFile": [
        "test1.eml",
        "test2.eml",
        "test3.eml"
    ],
    "updateDate": true,
    "updateMessageId": true,
    "useParallel": false
}"""
}

fun printUsage(): Unit {
    println("Usage: {self} json_file ...")
    println("---")
    println("json_file sample:")
    println(makeJsonSample())
}

fun printVersion() {
    println("SendEML / Version: $VERSION")
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }

    if (args[0] == "--version") {
        printVersion()
        return
    }

    for (json_file in args) {
        if (!File(json_file).exists()) {
            println("$json_file: Json file does not exists")
            continue
        }

        try {
            val settings = getSettings(json_file)
            if (settings != null) {
                if (settings.useParallel) {
                    useParallel = true
                    settings.emlFile.parallelStream().forEach { sendOneMessage(settings, it) }
                } else {
                    sendMessages(settings, settings.emlFile);
                }
            } else {
                println("$json_file: Failed to parse?")
            }
        } catch (e: Exception) {
            println("$json_file: ${e.message}")
        }
    }
}