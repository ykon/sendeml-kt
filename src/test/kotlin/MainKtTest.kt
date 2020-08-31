/**
 * Copyright (c) Yuki Ono.
 * Licensed under the MIT License.
 */

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.junit.jupiter.api.Assertions.*
import java.io.*
import kotlin.reflect.KFunction1
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertDoesNotThrow

typealias SendCmd = (String) -> String

internal class MainKtTest {
    private fun ByteArray.toUtf8String(): String {
        return this.toString(Charsets.UTF_8)
    }

    private fun makeSimpleMailText(): String {
        return """From: a001 <a001@ah62.example.jp>
Subject: test
To: a002@ah62.example.jp
Message-ID: <b0e564a5-4f70-761a-e103-70119d1bcb32@ah62.example.jp>
Date: Sun, 26 Jul 2020 22:01:37 +0900
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:78.0) Gecko/20100101
 Thunderbird/78.0.1
MIME-Version: 1.0
Content-Type: text/plain; charset=utf-8; format=flowed
Content-Transfer-Encoding: 7bit
Content-Language: en-US

test""".replace("\n", "\r\n")
    }

    private fun makeFoldedMail(): ByteArray {
        val text = """From: a001 <a001@ah62.example.jp>
Subject: test
To: a002@ah62.example.jp
Message-ID:
 <b0e564a5-4f70-761a-e103-70119d1bcb32@ah62.example.jp>
Date:
 Sun, 26 Jul 2020
 22:01:37 +0900
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:78.0) Gecko/20100101
 Thunderbird/78.0.1
MIME-Version: 1.0
Content-Type: text/plain; charset=utf-8; format=flowed
Content-Transfer-Encoding: 7bit
Content-Language: en-US

test"""
        return text.replace("\n", "\r\n").toByteArray()
    }

    private fun makeFoldedEndDate(): ByteArray {
        val text = """From: a001 <a001@ah62.example.jp>
Subject: test
To: a002@ah62.example.jp
Message-ID:
 <b0e564a5-4f70-761a-e103-70119d1bcb32@ah62.example.jp>
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:78.0) Gecko/20100101
 Thunderbird/78.0.1
MIME-Version: 1.0
Content-Type: text/plain; charset=utf-8; format=flowed
Content-Transfer-Encoding: 7bit
Content-Language: en-US
Date:
 Sun, 26 Jul 2020
 22:01:37 +0900
"""
        return text.replace("\n", "\r\n").toByteArray()
    }

    private fun makeFoldedEndMessageId(): ByteArray {
        val text = """From: a001 <a001@ah62.example.jp>
Subject: test
To: a002@ah62.example.jp
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:78.0) Gecko/20100101
 Thunderbird/78.0.1
MIME-Version: 1.0
Content-Type: text/plain; charset=utf-8; format=flowed
Content-Transfer-Encoding: 7bit
Content-Language: en-US
Date:
 Sun, 26 Jul 2020
 22:01:37 +0900
Message-ID:
 <b0e564a5-4f70-761a-e103-70119d1bcb32@ah62.example.jp>
"""
        return text.replace("\n", "\r\n").toByteArray()
    }

    private fun makeSimpleMail(): ByteArray {
        return makeSimpleMailText().toByteArray()
    }

    private fun makeInvalidMail(): ByteArray {
        return makeSimpleMailText().replace("\r\n\r\n", "").toByteArray()
    }

    private fun assertArrayNotEquals(a1: ByteArray, a2: ByteArray) {
        assertEquals(false, a1.contentEquals(a2))
    }

    private fun getHeaderLine(header: ByteArray, name: String): String {
        val headerStr = header.toUtf8String()
        val pat = """:[\s\S]+?\r\n(?=([^ \t]|$))"""
        return Regex(name + pat).find(headerStr)?.value!!
    }

    private fun getMessageIdLine(header: ByteArray): String {
        return getHeaderLine(header, "Message-ID")
    }

    private fun getDateLine(header: ByteArray): String {
        return getHeaderLine(header, "Date")
    }

    @org.junit.jupiter.api.Test
    fun getHeaderLineTest() {
        val mail = makeSimpleMail()
        assertEquals("Date: Sun, 26 Jul 2020 22:01:37 +0900\r\n", getDateLine(mail))
        assertEquals("Message-ID: <b0e564a5-4f70-761a-e103-70119d1bcb32@ah62.example.jp>\r\n", getMessageIdLine(mail))

        val fMail = makeFoldedMail()
        assertEquals("Date:\r\n Sun, 26 Jul 2020\r\n 22:01:37 +0900\r\n", getDateLine(fMail))
        assertEquals("Message-ID:\r\n <b0e564a5-4f70-761a-e103-70119d1bcb32@ah62.example.jp>\r\n", getMessageIdLine(fMail))

        val eDate = makeFoldedEndDate()
        assertEquals("Date:\r\n Sun, 26 Jul 2020\r\n 22:01:37 +0900\r\n", getDateLine(eDate))

        val eMessageId = makeFoldedEndMessageId()
        assertEquals("Message-ID:\r\n <b0e564a5-4f70-761a-e103-70119d1bcb32@ah62.example.jp>\r\n", getMessageIdLine(eMessageId))
    }

    @org.junit.jupiter.api.Test
    fun findCrIndex() {
        val mail = makeSimpleMail()
        assertEquals(33, app.findCrIndex(mail, 0))
        assertEquals(48, app.findCrIndex(mail, 34))
        assertEquals(74, app.findCrIndex(mail, 58))
    }

    @org.junit.jupiter.api.Test
    fun findLfIndex() {
        val mail = makeSimpleMail()
        assertEquals(34, app.findLfIndex(mail, 0))
        assertEquals(49, app.findLfIndex(mail, 35))
        assertEquals(75, app.findLfIndex(mail, 59))
    }

    @org.junit.jupiter.api.Test
    fun findAllLfIndices() {
        val mail = makeSimpleMail()
        val indices = app.findAllLfIndices(mail)

        assertEquals(34, indices[0])
        assertEquals(49, indices[1])
        assertEquals(75, indices[2])

        assertEquals(390, indices[indices.lastIndex - 2])
        assertEquals(415, indices[indices.lastIndex - 1])
        assertEquals(417, indices[indices.lastIndex])
    }

    @org.junit.jupiter.api.Test
    fun getRawLines() {
        val mail = makeSimpleMail()
        val lines = app.getRawLines(mail)

        assertEquals(13, lines.size)
        assertEquals("From: a001 <a001@ah62.example.jp>\r\n", lines[0].toUtf8String())
        assertEquals("Subject: test\r\n", lines[1].toUtf8String())
        assertEquals("To: a002@ah62.example.jp\r\n", lines[2].toUtf8String())

        assertEquals("Content-Language: en-US\r\n", lines[lines.lastIndex - 2].toUtf8String())
        assertEquals("\r\n", lines[lines.lastIndex - 1].toUtf8String())
        assertEquals("test", lines[lines.lastIndex].toUtf8String())
    }

    @org.junit.jupiter.api.Test
    fun matchHeader() {
        val test = { s1: String, s2: String -> app.matchHeader(s1.toByteArray(), s2.toByteArray()) }

        assertTrue(test("Test:", "Test:"))
        assertTrue(test("Test: ", "Test:"))
        assertTrue(test("Test: xxx", "Test:"))

        assertFalse(test("", "Test:"))
        assertFalse(test("T", "Test:"))
        assertFalse(test("Test", "Test:"))

        assertThrows<Exception> { test("Test: xxx", "") }
    }

    @org.junit.jupiter.api.Test
    fun isDateLine() {
        val test = { s: String -> app.isDateLine(s.toByteArray()) }

        assertTrue(test("Date: xxx"))
        assertTrue(test("Date:xxx"))
        assertTrue(test("Date:"))
        assertTrue(test("Date:   "))

        assertFalse(test(""))
        assertFalse(test("xxx: Date"))
        assertFalse(test("X-Date: xxx"))
    }

    @org.junit.jupiter.api.Test
    fun makeNowDateLine() {
        val line = app.makeNowDateLine()
        assertTrue(line.startsWith("Date: "))
        assertTrue(line.endsWith(app.CRLF))
        assertTrue(line.length <= 80)
    }

    @org.junit.jupiter.api.Test
    fun isMessageIdLine() {
        val test = { s: String -> app.isMessageIdLine(s.toByteArray()) }

        assertTrue(test("Message-ID: xxx"))
        assertTrue(test("Message-ID:xxx"))
        assertTrue(test("Message-ID:"))
        assertTrue(test("Message-ID:   "))

        assertFalse(test(""))
        assertFalse(test("Message-ID"))
        assertFalse(test("xxx: Message-ID"))
        assertFalse(test("X-Message-ID xxx"))
    }

    @org.junit.jupiter.api.Test
    fun makeRandomMessageIdLine() {
        val line = app.makeRandomMessageIdLine()
        assertTrue(line.startsWith("Message-ID: "))
        assertTrue(line.endsWith(app.CRLF))
        assertTrue(line.length <= 80)
    }

    @org.junit.jupiter.api.Test
    fun isNotUpdate() {
        assertFalse(app.isNotUpdate(true, true))
        assertFalse(app.isNotUpdate(true, false))
        assertFalse(app.isNotUpdate(false, true))
        assertTrue(app.isNotUpdate(false, false))
    }

    @org.junit.jupiter.api.Test
    fun isWsp() {
        assertTrue(app.isWsp(' '.toByte()))
        assertTrue(app.isWsp('\t'.toByte()))
        assertFalse(app.isWsp('0'.toByte()))
        assertFalse(app.isWsp('a'.toByte()))
        assertFalse(app.isWsp('b'.toByte()))
    }

    @org.junit.jupiter.api.Test
    fun isFirstWsp() {
        assertTrue(app.isFirstWsp(byteArrayOf(' '.toByte(), 'a'.toByte(), 'b'.toByte())))
        assertTrue(app.isFirstWsp(byteArrayOf('\t'.toByte(), 'a'.toByte(), 'b'.toByte())))
        assertFalse(app.isFirstWsp(byteArrayOf('0'.toByte(), 'a'.toByte(), 'b'.toByte())))
        assertFalse(app.isFirstWsp(byteArrayOf('a'.toByte(), 'b'.toByte(), ' '.toByte())))
        assertFalse(app.isFirstWsp(byteArrayOf('a'.toByte(), 'b'.toByte(), '\t'.toByte())))
    }

    private fun equalBytesInList(list1: List<ByteArray>, list2: List<ByteArray>): Boolean {
        if (list1.size != list2.size)
            return false

        return list1.zip(list2).all { it.first.contentEquals(it.second) }
    }

    @org.junit.jupiter.api.Test
    fun replaceDateLine() {
        val fMail = makeFoldedMail()
        val lines = app.getRawLines(fMail)
        val newLines = app.replaceDateLine(lines)
        assertFalse(equalBytesInList(lines, newLines))

        val newMail = app.concatBytes(newLines)
        assertArrayNotEquals(fMail, newMail)
        assertNotEquals(getDateLine(fMail), getDateLine(newMail))
        assertEquals(getMessageIdLine(fMail), getMessageIdLine(newMail))
    }

    @org.junit.jupiter.api.Test
    fun replaceMessageIdLine() {
        val fMail = makeFoldedMail()
        val lines = app.getRawLines(fMail)
        val newLines = app.replaceMessageIdLine(lines)
        assertFalse(equalBytesInList(lines, newLines))

        val newMail = app.concatBytes(newLines)
        assertArrayNotEquals(fMail, newMail)
        assertNotEquals(getMessageIdLine(fMail), getMessageIdLine(newMail))
        assertEquals(getDateLine(fMail), getDateLine(newMail))
    }

    @org.junit.jupiter.api.Test
    fun replaceHeader() {
        val mail = makeSimpleMail()
        val dateLine = getDateLine(mail)
        val midLine = getMessageIdLine(mail)

        val replHeaderNoupdate = app.replaceHeader(mail, false, false)
        assertArrayEquals(mail, replHeaderNoupdate)

        val replHeader = app.replaceHeader(mail, true, true)
        assertArrayNotEquals(mail, replHeader)

        fun replace(header: ByteArray, update_date: Boolean, update_message_id: Boolean): Pair<String, String> {
            val rHeader = app.replaceHeader(header, update_date, update_message_id)
            assertArrayNotEquals(header, rHeader)
            return Pair(getDateLine(rHeader), getMessageIdLine(rHeader))
        }

        val (rDateLine, rMidLine) = replace(mail, true, true)
        assertNotEquals(dateLine, rDateLine)
        assertNotEquals(midLine, rMidLine)

        val (rDateLine2, rMidLine2) = replace(mail, true, false)
        assertNotEquals(dateLine, rDateLine2)
        assertEquals(midLine, rMidLine2)

        val (rDateLine3, rMidLine3) = replace(mail, false, true)
        assertEquals(dateLine, rDateLine3)
        assertNotEquals(midLine, rMidLine3)

        val fMail = makeFoldedMail()
        val (fDateLine, fMidLine) = replace(fMail, true, true)
        assertEquals(1, fDateLine.count { it == '\n' })
        assertEquals(1, fMidLine.count { it == '\n' })
    }

    @org.junit.jupiter.api.Test
    fun concatBytes() {
        val mail = makeSimpleMail()
        val lines = app.getRawLines(mail)

        val newMail = app.concatBytes(lines)
        assertArrayEquals(mail, newMail)
    }

    @org.junit.jupiter.api.Test
    fun combineMail() {
        val mail = makeSimpleMail()
        val (header, body) = app.splitMail(mail)!!
        val newMail = app.combineMail(header, body)
        assertArrayEquals(mail, newMail)
    }

    @org.junit.jupiter.api.Test
    fun findEmptyLine() {
        val mail = makeSimpleMail()
        assertEquals(414, app.findEmptyLine(mail))

        val invalidMail = makeInvalidMail()
        assertEquals(-1, app.findEmptyLine(invalidMail))
    }

    @org.junit.jupiter.api.Test
    fun splitMail() {
        val mail = makeSimpleMail()
        val headerBody = app.splitMail(mail)
        assertTrue(headerBody != null)

        val (header, body) = headerBody!!
        assertArrayEquals(mail.take(414).toByteArray(), header)
        assertArrayEquals(mail.drop(414 + 4).toByteArray(), body)

        val invalidMail = makeInvalidMail()
        assertTrue(app.splitMail(invalidMail) == null)
    }

    @org.junit.jupiter.api.Test
    fun replaceMail() {
        val mail = makeSimpleMail()
        val replMailNoupdate = app.replaceMail(mail, false, false)
        assertEquals(mail, replMailNoupdate)

        val replMail = app.replaceMail(mail, true, true)
        assertNotEquals(mail, replMail)
        assertArrayNotEquals(mail, replMail!!)

        val mailLast100 = mail.sliceArray((mail.size - 100) until mail.size)
        val replMailLast100 = replMail.sliceArray((replMail.size - 100) until replMail.size)
        assertArrayEquals(mailLast100, replMailLast100)

        val invalidMail = makeInvalidMail()
        assertArrayEquals(null, app.replaceMail(invalidMail, true, true))
    }

    @org.junit.jupiter.api.Test
    fun getAndMapSettings() {
        val file = createTempFile()
        file.writeText(app.makeJsonSample())

        val settings = app.mapSettings(app.getSettings(file.path))
        assertEquals("172.16.3.151", settings.smtpHost)
        assertEquals(25, settings.smtpPort)
        assertEquals("a001@ah62.example.jp", settings.fromAddress)
        assertTrue(listOf("a001@ah62.example.jp", "a002@ah62.example.jp", "a003@ah62.example.jp")
                == settings.toAddresses)
        assertTrue(listOf("test1.eml", "test2.eml", "test3.eml")
                == settings.emlFiles)
        assertEquals(true, settings.updateDate)
        assertEquals(true, settings.updateMessageId)
        assertEquals(false, settings.useParallel)
    }

    @org.junit.jupiter.api.Test
    fun isLastReply() {
        assertFalse(app.isLastReply("250-First line"))
        assertFalse(app.isLastReply("250-Second line"))
        assertFalse(app.isLastReply("250-234 Text beginning with numbers"))
        assertTrue(app.isLastReply("250 The last line"))
    }

    @org.junit.jupiter.api.Test
    fun isPositiveReply() {
        assertTrue(app.isPositiveReply("200 xxx"))
        assertTrue(app.isPositiveReply("300 xxx"))
        assertFalse(app.isPositiveReply("400 xxx"))
        assertFalse(app.isPositiveReply("500 xxx"))
        assertFalse(app.isPositiveReply("xxx 200"))
        assertFalse(app.isPositiveReply("xxx 300"))
    }

    private fun useSetStdout(block: () -> Unit) {
        val origOut = System.out
        try {
            block()
        } finally {
            System.setOut(origOut)
        }
    }

    private fun getStdout(block: () -> Unit): String {
        val stdOutStream = ByteArrayOutputStream()
        useSetStdout {
            System.setOut(PrintStream(stdOutStream))
            block()
        }
        return stdOutStream.toString()
    }

    @org.junit.jupiter.api.Test
    fun sendMail() {
        val file = createTempFile()
        val mail = makeSimpleMail()
        file.writeBytes(mail)

        val fileOutStream = ByteArrayOutputStream()
        val sendLine = getStdout {
            app.sendMail(fileOutStream, file.path, false, false)
        }
        assertEquals("send: ${file.path}\r\n", sendLine)
        assertArrayEquals(mail, fileOutStream.toByteArray())

        val fileOutStream2 = ByteArrayOutputStream()
        app.sendMail(fileOutStream2, file.path, true, true)
        assertArrayNotEquals(mail, fileOutStream2.toByteArray())
    }

    @org.junit.jupiter.api.Test
    fun recvLine() {
        val recvLine = getStdout {
            val bufReader = BufferedReader(StringReader("250 OK\r\n"))
            assertEquals("250 OK", app.recvLine(bufReader))
        }
        assertEquals("recv: 250 OK\r\n", recvLine)

        val bufReader2 = BufferedReader(StringReader(""))
        assertThrows<Exception> { app.recvLine(bufReader2) }

        val bufReader3 = BufferedReader(StringReader("554 Transaction failed\r\n"))
        assertThrows<Exception> { app.recvLine(bufReader3) }
    }

    @org.junit.jupiter.api.Test
    fun replaceCrlfDot() {
        assertEquals("TEST", app.replaceCrlfDot("TEST"))
        assertEquals("CRLF", app.replaceCrlfDot("CRLF"))
        assertEquals(app.CRLF, app.replaceCrlfDot(app.CRLF))
        assertEquals(".", app.replaceCrlfDot("."))
        assertEquals("<CRLF>.", app.replaceCrlfDot("${app.CRLF}."))
    }

    @org.junit.jupiter.api.Test
    fun sendLine() {
        val output = ByteArrayOutputStream()
        val sendLine = getStdout {
            app.sendLine(output, "EHLO localhost")
        }
        assertEquals("send: EHLO localhost\r\n", sendLine)
        assertEquals("EHLO localhost\r\n", output.toString(Charsets.UTF_8))
    }

    private fun makeTestSendCmd(expected: String): SendCmd {
        return { cmd ->
            assertEquals(expected, cmd)
            cmd
        }
    }

    @org.junit.jupiter.api.Test
    fun sendHello() {
        app.sendHello(makeTestSendCmd("EHLO localhost"))
    }

    @org.junit.jupiter.api.Test
    fun sendFrom() {
        app.sendFrom(makeTestSendCmd("MAIL FROM: <a001@ah62.example.jp>"), "a001@ah62.example.jp")
    }

    @org.junit.jupiter.api.Test
    fun sendRcptTo() {
        var count = 1
        val test: SendCmd = { cmd ->
            assertEquals("RCPT TO: <a00$count@ah62.example.jp>", cmd)
            count += 1
            cmd
        }

        app.sendRcptTo(test, listOf("a001@ah62.example.jp", "a002@ah62.example.jp", "a003@ah62.example.jp"))
    }

    @org.junit.jupiter.api.Test
    fun sendData() {
        app.sendData(makeTestSendCmd("DATA"))
    }

    @org.junit.jupiter.api.Test
    fun sendCrlfDot() {
        app.sendCrlfDot(makeTestSendCmd("\r\n."))
    }

    @org.junit.jupiter.api.Test
    fun sendQuit() {
        app.sendQuit(makeTestSendCmd("QUIT"))
    }

    @org.junit.jupiter.api.Test
    fun sendRset() {
        app.sendRset(makeTestSendCmd("RSET"))
    }

    @org.junit.jupiter.api.Test
    fun printVersion() {
        val version = getStdout {
            app.printVersion()
        }
        assertTrue(version.contains("Version:"))
        assertTrue(version.contains(app.VERSION.toString()))
    }

    @org.junit.jupiter.api.Test
    fun printUsage() {
        val usage = getStdout {
            app.printUsage()
        }
        assertTrue(usage.contains("Usage:"))
    }

    @org.junit.jupiter.api.Test
    fun checkSettings() {
        fun checkNoKey(key: String) {
            val json = app.makeJsonSample()
            val noKey = json.replace(key, "X-$key")
            app.checkSettings(app.getSettingsFromText(noKey))
        }

        assertThrows<Exception> { checkNoKey("smtpHost") }
        assertThrows<Exception> { checkNoKey("smtpPort") }
        assertThrows<Exception> { checkNoKey("fromAddress") }
        assertThrows<Exception> { checkNoKey("toAddresses") }
        assertThrows<Exception> { checkNoKey("emlFiles") }

        assertDoesNotThrow {
            checkNoKey("updateDate")
            checkNoKey("updateMessage")
            checkNoKey("useParallel")
        }
    }

    @org.junit.jupiter.api.Test
    fun procJsonFile() {
        assertThrows<Exception> { app.procJsonFile("__test__") }
    }

    private fun String.toJson(): JsonObject {
        return Parser.default().parse(StringBuilder(this)) as JsonObject
    }

    @org.junit.jupiter.api.Test
    fun checkJsonValue() {
        fun <T> check(jsonStr: JsonObject, check: KFunction1<String, T?>) {
            app.checkJsonValue(jsonStr, "test", check)
        }

        fun <T> checkError(jsonStr: JsonObject, check: KFunction1<String, T?>, expected: String) {
            try {
                check(jsonStr, check)
            } catch (e: Exception) {
                assertEquals(expected, e.message)
            }
        }

        val jsonStr = """{"test": "172.16.3.151"}""".toJson()
        val jsonNumber = """{"test": 172}""".toJson()
        val jsonTrue = """{"test": true}""".toJson()
        val jsonFalse = """{"test": false}""".toJson()

        assertDoesNotThrow {
            check(jsonStr, jsonStr::string)
            check(jsonNumber, jsonNumber::int)
            check(jsonTrue, jsonTrue::boolean)
            check(jsonFalse, jsonFalse::boolean)
        }

        assertThrows<Exception> { check(jsonStr, jsonStr::int) }
        checkError(jsonStr, jsonStr::boolean, "test: Invalid type: 172.16.3.151")

        assertThrows<Exception> { check(jsonNumber, jsonNumber::string) }
        checkError(jsonNumber, jsonNumber::boolean, "test: Invalid type: 172")

        assertThrows<Exception> { check(jsonTrue, jsonTrue::string) }
        checkError(jsonTrue, jsonTrue::int, "test: Invalid type: true")

        assertThrows<Exception> { check(jsonFalse, jsonFalse::string) }
        checkError(jsonFalse, jsonFalse::int, "test: Invalid type: false")
    }

    @org.junit.jupiter.api.Test
    fun checkJsonStringArrayValue() {
        fun check(jsonStr: String) {
            app.checkJsonStringArrayValue(jsonStr.toJson(), "test")
        }

        fun checkError(jsonStr: String, expected: String) {
            try {
                check(jsonStr)
            } catch (e: Exception) {
                assertEquals(expected, e.message)
            }
        }

        val jsonArray = """{"test": ["172.16.3.151", "172.16.3.152", "172.16.3.153"]}"""
        assertDoesNotThrow { check(jsonArray) }

        val jsonStr = """{"test": "172.16.3.151"}"""
        checkError(jsonStr, "test: Invalid type (array): 172.16.3.151")

        val jsonInvalidArray = """{"test": ["172.16.3.151", "172.16.3.152", 172]}"""
        checkError(jsonInvalidArray, "test: Invalid type (element): 172")
    }
}