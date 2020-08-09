/**
 * Copyright (c) Yuki Ono.
 * Licensed under the MIT License.
 */

package app

import com.beust.klaxon.Klaxon
import java.io.*
import java.net.InetAddress
import java.net.Socket
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

const val VERSION = 1.3

const val CR = '\r'.toByte()
const val LF = '\n'.toByte()
const val CRLF = "\r\n"

fun indexOf(buf: ByteArray, value: Byte, offset: Int): Int {
    for (i in offset until buf.size) {
        if (buf[i] == value)
            return i
    }
    return -1
}

fun findLfIndex(fileBuf: ByteArray, offset: Int): Int {
    return indexOf(fileBuf, LF, offset)
}

fun findAllLfIndices(fileBuf: ByteArray): List<Int> {
    val indices = mutableListOf<Int>()
    var offset = 0
    while (true) {
        val idx = findLfIndex(fileBuf, offset)
        if (idx == -1)
            return indices

        indices.add(idx)
        offset = idx + 1
    }
}

fun getRawLines(fileBuf: ByteArray): List<ByteArray> {
    var offset = 0
    return findAllLfIndices(fileBuf).plus(fileBuf.lastIndex).map {
        val line = fileBuf.copyOfRange(offset, it + 1)
        offset = it + 1
        return@map line
    }
}

val DATE_BYTES = "Date:".toByteArray(Charsets.UTF_8)
val MESSAGE_ID_BYTES = "Message-ID:".toByteArray(Charsets.UTF_8)

fun matchHeaderField(line: ByteArray, header: ByteArray): Boolean {
    if (line.size < header.size)
        return false

    for (i in header.indices) {
        if (header[i] != line[i])
            return false
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
    val randStr = (1..length).map { chars.random() }.joinToString("")
    return "Message-ID: <$randStr>$CRLF"
}

fun isNotUpdate(updateDate: Boolean, updateMessageId: Boolean): Boolean {
    return !updateDate && !updateMessageId
}

fun replaceRawLines(lines: List<ByteArray>, updateDate: Boolean, updateMessageId: Boolean): List<ByteArray> {
    if (isNotUpdate(updateDate, updateMessageId))
        return lines

    fun replaceLine(lines: MutableList<ByteArray>, update: Boolean, matchLine: (ByteArray) -> Boolean, makeLine: () -> String): Unit {
        if (update) {
            val idx = lines.indexOfFirst(matchLine)
            if (idx != -1)
                lines[idx] = makeLine().toByteArray(Charsets.UTF_8)
        }
    }

    val replLines = lines.toMutableList()
    replaceLine(replLines, updateDate, ::isDateLine, ::makeNowDateLine)
    replaceLine(replLines, updateMessageId, ::isMessageIdLine, ::makeRandomMessageIdLine)
    return replLines
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

fun findEmptyLine(fileBuf: ByteArray): Int {
    var offset = 0
    while (true) {
        val idx = indexOf(fileBuf, CR, offset)
        if (idx == -1 || (idx + 3) >= fileBuf.size)
            return -1

        if (fileBuf[idx + 1] == LF && fileBuf[idx + 2] == CR && fileBuf[idx + 3] == LF)
            return idx

        offset = idx + 1
    }
}

val EMPTY_LINE = arrayOf(CR, LF, CR, LF).toByteArray()

fun combineMail(header: ByteArray, body: ByteArray): ByteArray {
    val mail = ByteArray(header.size + EMPTY_LINE.size + body.size)
    header.copyInto(mail, 0, 0, header.size)
    EMPTY_LINE.copyInto(mail, header.size, 0, EMPTY_LINE.size)
    body.copyInto(mail, header.size + EMPTY_LINE.size, 0, body.size)
    return mail
}

fun splitMail(fileBuf: ByteArray): Pair<ByteArray, ByteArray>? {
    val idx = findEmptyLine(fileBuf)
    if (idx == -1)
        return null

    val header = fileBuf.copyOfRange(0, idx)
    val body = fileBuf.copyOfRange(idx + EMPTY_LINE.size, fileBuf.size)
    return Pair(header, body)
}

fun replaceRawBytes(fileBuf: ByteArray, updateDate: Boolean, updateMessageId: Boolean): ByteArray {
    if (isNotUpdate(updateDate, updateMessageId))
        return fileBuf

    val (header, body) = splitMail(fileBuf) ?: throw IOException("Invalid mail")
    val replHeader = concatRawLines(replaceRawLines(getRawLines(header), updateDate, updateMessageId))
    return combineMail(replHeader, body)
}

@Volatile var USE_PARALLEL = false

fun getCurrentIdPrefix(): String {
    return if (USE_PARALLEL) "id: ${Thread.currentThread().id}, " else ""
}

fun sendRawBytes(output: OutputStream, file: String, updateDate: Boolean, updateMessageId: Boolean): Unit {
    println(getCurrentIdPrefix() + "send: $file")

    val buf = replaceRawBytes(File(file).readBytes(), updateDate, updateMessageId)
    output.write(buf)
    output.flush()
}

data class Settings (
    val smtpHost: String? = null,
    val smtpPort: Int? = null,
    val fromAddress: String? = null,
    val toAddress: List<String>? = null,
    val emlFile: List<String>? = null,
    val updateDate: Boolean = true,
    val updateMessageId: Boolean = true,
    val useParallel: Boolean = false
)

fun getSettingsFromText(text: String): Settings? {
    return Klaxon().parse<Settings>(text)
}

fun getSettings(file: String): Settings? {
    return getSettingsFromText(File(file).readText())
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

fun recvLine(reader: BufferedReader): String {
    while (true) {
        val line = reader.readLine()?.trim() ?: throw IOException("Connection closed by foreign host")
        println(getCurrentIdPrefix() + "recv: $line")

        if (isLastReply(line)) {
            if (isPositiveReply(line))
                return line

            throw IOException(line)
        }
    }
}

fun replaceCrlfDot(cmd: String): String {
    return if (cmd == "$CRLF.") "<CRLF>." else cmd
}

fun sendLine(output: OutputStream, cmd: String): Unit {
    println(getCurrentIdPrefix() + "send: " + replaceCrlfDot(cmd))

    output.write((cmd + CRLF).toByteArray())
    output.flush()
}

typealias SendCmd = (String) -> String

fun makeSendCmd(reader: BufferedReader, output: OutputStream): SendCmd {
    return { cmd ->
        sendLine(output, cmd)
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

fun sendCrlfDot(send: SendCmd): Unit {
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
    Socket(addr, settings.smtpPort!!).use { socket ->
        socket.soTimeout = 1000

        val bufReader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val output = socket.getOutputStream()
        val send = makeSendCmd(bufReader, output)

        recvLine(bufReader)
        sendHello(send)

        var mailSent = false
        for (file in emlFiles) {
            if (!File(file).exists()) {
                println("$file: EML file does not exist")
                continue
            }

            if (mailSent) {
                println("---")
                sendRset(send)
            }

            sendFrom(send, settings.fromAddress!!)
            sendRcptTo(send, settings.toAddress!!)
            sendData(send)
            sendRawBytes(output, file, settings.updateDate, settings.updateMessageId)
            sendCrlfDot(send)
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

fun checkSettings(settings: Settings): Unit {
    fun getNullKey(s: Settings): String {
        return when {
            s.smtpHost == null -> "smtpHost"
            s.smtpPort == null -> "smtpPort"
            s.fromAddress == null -> "fromAddress"
            s.toAddress == null -> "toAddress"
            s.emlFile == null -> "emlFile"
            else -> ""
        }
    }

    val nullKey = getNullKey(settings)
    if (nullKey.isNotEmpty())
        throw IOException("$nullKey key does not exist")
}

fun procJsonFile(json_file: String): Unit {
    if (!File(json_file).exists())
        throw IOException("Json file does not exist")

    val settings = getSettings(json_file) ?: throw IOException("Failed to parse")
    checkSettings(settings)

    if (settings.useParallel) {
        USE_PARALLEL = true
        settings.emlFile!!.parallelStream().forEach { sendOneMessage(settings, it) }
    } else {
        sendMessages(settings, settings.emlFile!!);
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
            println("$json_file: ${e.message}")
        }
    }
}