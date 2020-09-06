/**
 * Copyright (c) Yuki Ono.
 * Licensed under the MIT License.
 */

package app

import java.io.*
import java.net.InetAddress
import java.net.Socket
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import kotlin.reflect.KFunction1

const val VERSION = 1.5

const val CR = '\r'.toByte()
const val LF = '\n'.toByte()
const val SPACE = ' '.toByte()
const val HTAB = '\t'.toByte()
const val CRLF = "\r\n"

fun findIndex(buf: ByteArray, value: Byte, offset: Int): Int {
    for (i in offset until buf.size) {
        if (buf[i] == value)
            return i
    }
    return -1
}

fun findCr(bytes: ByteArray, offset: Int): Int {
    return findIndex(bytes, CR, offset)
}

fun findLf(bytes: ByteArray, offset: Int): Int {
    return findIndex(bytes, LF, offset)
}

fun findAllLf(bytes: ByteArray): List<Int> {
    val indices = mutableListOf<Int>()
    var offset = 0
    while (true) {
        val idx = findLf(bytes, offset)
        if (idx == -1)
            return indices

        indices.add(idx)
        offset = idx + 1
    }
}

fun getLines(bytes: ByteArray): List<ByteArray> {
    var offset = 0
    return findAllLf(bytes).plus(bytes.lastIndex).map {
        val line = bytes.sliceArray(offset..it)
        offset = it + 1
        return@map line
    }
}

val DATE_BYTES = "Date:".toByteArray()
val MESSAGE_ID_BYTES = "Message-ID:".toByteArray()

fun matchHeader(line: ByteArray, header: ByteArray): Boolean {
    if (header.isEmpty())
        throw Exception("header is empty")

    if (line.size < header.size)
        return false

    return line.sliceArray(header.indices).contentEquals(header)
}

fun isDateLine(line: ByteArray): Boolean {
    return matchHeader(line, DATE_BYTES)
}

fun makeNowDateLine(): String {
    val time = OffsetDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
    return "Date: " + time.format(formatter) + CRLF
}

fun isMessageIdLine(line: ByteArray): Boolean {
    return matchHeader(line, MESSAGE_ID_BYTES)
}

fun makeRandomMessageIdLine(): String {
    val chars =  ('a'..'z') + ('A'..'Z') + ('0'..'9')
    val length = 62
    val randStr = (1..length).map { chars.random() }.joinToString("")
    return "Message-ID: <$randStr>$CRLF"
}

fun isNotUpdate(updateDate: Boolean, updateMessageId: Boolean): Boolean {
    return !updateDate && !updateMessageId
}

fun concatBytes(bytesList: Iterable<ByteArray>): ByteArray {
    val buf = ByteArray(bytesList.sumBy { it.size })
    var offset = 0
    for (b in bytesList) {
        b.copyInto(buf, offset, 0, b.size)
        offset += b.size
    }
    return buf
}

fun isWsp(b: Byte): Boolean {
    return b == SPACE || b == HTAB
}

fun isFirstWsp(bytes: ByteArray): Boolean {
    return isWsp(bytes.firstOrNull() ?: 0)
}

private fun replaceLine(lines: List<ByteArray>, matchLine: (ByteArray) -> Boolean, makeLine: () -> String): List<ByteArray> {
    val idx = lines.indexOfFirst(matchLine)
    if (idx == -1)
        return lines

    val p1 = lines.take(idx)
    val p2 = makeLine().toByteArray()
    val p3 = lines.drop(idx + 1).dropWhile(::isFirstWsp)

    return p1 + p2 + p3
}

fun replaceDateLine(lines: List<ByteArray>): List<ByteArray> {
    return replaceLine(lines, ::isDateLine, ::makeNowDateLine)
}

fun replaceMessageIdLine(lines: List<ByteArray>): List<ByteArray> {
    return replaceLine(lines, ::isMessageIdLine, ::makeRandomMessageIdLine)
}

fun replaceHeader(header: ByteArray, updateDate: Boolean, updateMessageId: Boolean): ByteArray {
    val lines = getLines(header)
    val newLines = when (Pair(updateDate, updateMessageId)) {
        Pair(true, true) -> replaceMessageIdLine(replaceDateLine(lines))
        Pair(true, false) -> replaceDateLine(lines)
        Pair(false, true) -> replaceMessageIdLine(lines)
        else -> lines
    }
    return concatBytes(newLines)
}

fun hasNextLfCrLf(bytes: ByteArray, idx: Int): Boolean {
    return if (bytes.size < (idx + 4)) false
        else bytes.sliceArray((idx + 1)..(idx + 3)).contentEquals(byteArrayOf(LF, CR, LF))
}

fun findEmptyLine(bytes: ByteArray): Int {
    var offset = 0
    while (true) {
        val idx = findCr(bytes, offset)
        if (idx == -1)
            return -1
        if (hasNextLfCrLf(bytes, idx))
            return idx

        offset = idx + 1
    }
}

val EMPTY_LINE = arrayOf(CR, LF, CR, LF).toByteArray()

fun combineMail(header: ByteArray, body: ByteArray): ByteArray {
    return concatBytes(listOf(header, EMPTY_LINE, body))
}

fun splitMail(bytes: ByteArray): Pair<ByteArray, ByteArray>? {
    val idx = findEmptyLine(bytes)
    if (idx == -1)
        return null

    val header = bytes.sliceArray(0 until idx)
    val body = bytes.sliceArray((idx + EMPTY_LINE.size) until bytes.size)
    return Pair(header, body)
}

fun replaceMail(bytes: ByteArray, updateDate: Boolean, updateMessageId: Boolean): ByteArray? {
    if (isNotUpdate(updateDate, updateMessageId))
        return bytes

    val (header, body) =  splitMail(bytes) ?: return null
    val replHeader = replaceHeader(header, updateDate, updateMessageId)
    return combineMail(replHeader, body)
}

fun makeIdPrefix(useParallel: Boolean): String {
    return if (useParallel) "id: ${Thread.currentThread().id}, " else ""
}

fun sendMail(output: OutputStream, file: String, updateDate: Boolean, updateMessageId: Boolean, useParallel: Boolean = false) {
    println(makeIdPrefix(useParallel) + "send: $file")

    val mail = File(file).readBytes()
    val replMail = replaceMail(mail, updateDate, updateMessageId)
    if (replMail == null)
        println("error: Invalid mail: Disable updateDate, updateMessageId")

    output.write(replMail ?: mail)
    output.flush()
}

data class Settings (
    val smtpHost: String,
    val smtpPort: Int,
    val fromAddress: String,
    val toAddresses: List<String>,
    val emlFiles: List<String>,
    val updateDate: Boolean,
    val updateMessageId: Boolean,
    val useParallel: Boolean
)

fun getSettingsFromText(text: String): JsonObject {
    return Parser.default().parse(StringBuilder(text)) as JsonObject
}

fun getSettings(file: String): JsonObject {
    return getSettingsFromText(File(file).readText())
}

fun mapSettings(json: JsonObject): Settings {
    return Settings(
        json.string("smtpHost")!!,
        json.int("smtpPort")!!,
        json.string("fromAddress")!!,
        json.array<String>("toAddresses")!!.toList(),
        json.array<String>("emlFiles")!!.toList(),
        json.boolean("updateDate") ?: true,
        json.boolean("updateMessageId") ?: true,
        json.boolean("useParallel") ?: false
    )
}

fun <T> checkJsonValue(json: JsonObject, name: String, check: KFunction1<String, T?>) {
    if (name in json) {
        try {
            check(name)
        } catch (_: Exception) {
            throw Exception("$name: Invalid type: ${json[name]}")
        }
    }
}

fun checkJsonStringArrayValue(json: JsonObject, name: String) {
    if (name in json) {
        val elm = try {
            json.array<Any>(name)!!.find { it !is String }
        } catch (_: Exception) {
            throw Exception("$name: Invalid type (array): ${json[name]}")
        }

        if (elm != null)
            throw Exception("$name: Invalid type (element): $elm")
    }
}

fun checkSettings(json: JsonObject) {
    val names = listOf("smtpHost", "smtpPort", "fromAddress", "toAddresses", "emlFiles")
    val key = names.find { it !in json }
    if (key != null)
        throw Exception("$key key does not exist")

    checkJsonValue(json, "smtpHost", json::string)
    checkJsonValue(json, "smtpPort", json::int)
    checkJsonValue(json, "fromAddress", json::string)
    checkJsonStringArrayValue(json, "toAddresses")
    checkJsonStringArrayValue(json, "emlFiles")
    checkJsonValue(json, "updateDate", json::boolean)
    checkJsonValue(json, "updateMessageId", json::boolean)
    checkJsonValue(json, "useParallel", json::boolean)
}

val LAST_REPLY_REGEX = Regex("""^\d{3} .+""")

fun isLastReply(line: String): Boolean {
    return LAST_REPLY_REGEX.containsMatchIn(line)
}

fun isPositiveReply(line: String): Boolean {
    return when (line.firstOrNull() ?: '0') {
        '2', '3' -> true
        else -> false
    }
}

fun recvLine(reader: BufferedReader, useParallel: Boolean = false): String {
    while (true) {
        val line = reader.readLine()?.trim() ?: throw Exception("Connection closed by foreign host")
        println(makeIdPrefix(useParallel) + "recv: $line")

        if (isLastReply(line)) {
            if (isPositiveReply(line))
                return line

            throw Exception(line)
        }
    }
}

fun replaceCrlfDot(cmd: String): String {
    return if (cmd == "$CRLF.") "<CRLF>." else cmd
}

fun sendLine(output: OutputStream, cmd: String, useParallel: Boolean = false) {
    println(makeIdPrefix(useParallel) + "send: " + replaceCrlfDot(cmd))

    output.write((cmd + CRLF).toByteArray())
    output.flush()
}

typealias SendCmd = (String) -> String

fun makeSendCmd(reader: BufferedReader, output: OutputStream, useParallel: Boolean): SendCmd {
    return { cmd ->
        sendLine(output, cmd, useParallel)
        recvLine(reader, useParallel)
    }
}

fun sendHello(send: SendCmd) {
    send("EHLO localhost")
}

fun sendFrom(send: SendCmd, fromAddr: String) {
    send("MAIL FROM: <$fromAddr>")
}

fun sendRcptTo(send: SendCmd, toAddrs: List<String>) {
    for (addr in toAddrs)
        send("RCPT TO: <$addr>")
}

fun sendData(send: SendCmd) {
    send("DATA")
}

fun sendCrlfDot(send: SendCmd) {
    send("$CRLF.")
}

fun sendQuit(send: SendCmd) {
    send("QUIT")
}

fun sendRset(send: SendCmd) {
    send("RSET")
}

fun sendMessages(settings: Settings, emlFiles: List<String>, useParallel: Boolean) {
    val addr = InetAddress.getByName(settings.smtpHost)
    Socket(addr, settings.smtpPort).use { socket ->
        val bufReader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val output = socket.getOutputStream()
        val send = makeSendCmd(bufReader, output, useParallel)

        recvLine(bufReader, useParallel)
        sendHello(send)

        var reset = false
        for (file in emlFiles) {
            if (!File(file).exists()) {
                println("$file: EML file does not exist")
                continue
            }

            if (reset) {
                println("---")
                sendRset(send)
            }

            sendFrom(send, settings.fromAddress)
            sendRcptTo(send, settings.toAddresses)
            sendData(send)
            sendMail(output, file, settings.updateDate, settings.updateMessageId, useParallel)
            sendCrlfDot(send)
            reset = true
        }

        sendQuit(send)
    }
}

fun makeJsonSample(): String {
    return """{
    "smtpHost": "172.16.3.151",
    "smtpPort": 25,
    "fromAddress": "a001@ah62.example.jp",
    "toAddresses": [
        "a001@ah62.example.jp",
        "a002@ah62.example.jp",
        "a003@ah62.example.jp"
    ],
    "emlFiles": [
        "test1.eml",
        "test2.eml",
        "test3.eml"
    ],
    "updateDate": true,
    "updateMessageId": true,
    "useParallel": false
}"""
}

fun printUsage() {
    println("Usage: {self} json_file ...")
    println("---")
    println("json_file sample:")
    println(makeJsonSample())
}

fun printVersion() {
    println("SendEML / Version: $VERSION")
}

fun procJsonFile(json_file: String) {
    if (!File(json_file).exists())
        throw Exception("Json file does not exist")

    val json = getSettings(json_file)
    checkSettings(json)
    val settings = mapSettings(json)

    if (settings.useParallel && settings.emlFiles.size > 1) {
        settings.emlFiles.parallelStream().forEach {
            sendMessages(settings, listOf(it), true)
        }
    } else {
        sendMessages(settings, settings.emlFiles, false)
    }
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
        try {
            procJsonFile(json_file)
        } catch (e: Exception) {
            println("error: $json_file: ${e.message}")
        }
    }
}