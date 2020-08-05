/**
 * Copyright (c) Yuki Ono.
 * Licensed under the MIT License.
 */

import org.junit.jupiter.api.Assertions.*
import java.io.*

typealias SendCmd = (String) -> String

internal class MainKtTest {
    private fun makeSimpleMail(): String {
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

test""";
    }

    private fun getMailByteArray(mail: String): ByteArray {
        return mail.replace("\n", "\r\n").toByteArray(Charsets.UTF_8)
    }

    @org.junit.jupiter.api.Test
    fun findLfIndex() {
        val mail = getMailByteArray(makeSimpleMail())
        assertEquals(34, app.findLfIndex(mail, 0))
        assertEquals(49, app.findLfIndex(mail, 35))
        assertEquals(75, app.findLfIndex(mail, 59))
    }

    @org.junit.jupiter.api.Test
    fun findAllLfIndices() {
        val mail = getMailByteArray(makeSimpleMail())
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
        val mail = getMailByteArray(makeSimpleMail())
        val lines = app.getRawLines(mail)

        assertEquals(13, lines.size)
        assertEquals("From: a001 <a001@ah62.example.jp>\r\n", lines[0].toString(Charsets.UTF_8))
        assertEquals("Subject: test\r\n", lines[1].toString(Charsets.UTF_8))
        assertEquals("To: a002@ah62.example.jp\r\n", lines[2].toString(Charsets.UTF_8))

        assertEquals("Content-Language: en-US\r\n", lines[lines.lastIndex - 2].toString(Charsets.UTF_8))
        assertEquals("\r\n", lines[lines.lastIndex - 1].toString(Charsets.UTF_8))
        assertEquals("test", lines[lines.lastIndex].toString(Charsets.UTF_8))
    }

    @org.junit.jupiter.api.Test
    fun matchHeaderField() {
        val test = { s1: String, s2: String -> app.matchHeaderField(s1.toByteArray(Charsets.UTF_8), s2.toByteArray(Charsets.UTF_8)) }

        assertTrue(test("Test:", "Test:"));
        assertTrue(test("Test: ", "Test:"));
        assertTrue(test("Test:x", "Test:"));

        assertFalse(test("", "Test:"));
        assertFalse(test("T", "Test:"));
        assertFalse(test("Test", "Test:"));
    }

    @org.junit.jupiter.api.Test
    fun isDateLine() {
        val test = { s: String -> app.isDateLine(s.toByteArray(Charsets.UTF_8)) }

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
        val test = { s: String -> app.isMessageIdLine(s.toByteArray(Charsets.UTF_8)) }

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
    fun replaceRawLines() {
        val mail = getMailByteArray(makeSimpleMail())
        val lines = app.getRawLines(mail)

        val replLinesNoupdate = app.replaceRawLines(lines, false, false)
        assertEquals(lines, replLinesNoupdate);

        val replLines = app.replaceRawLines(lines, true, true)

        for (i in (0 until 10).filter { it < 3 || it > 4 })
            assertEquals(lines[i], replLines[i])

        assertNotEquals(lines[3], replLines[3])
        assertNotEquals(lines[4], replLines[4])

        assertTrue(lines[3].toString(Charsets.UTF_8).startsWith("Message-ID: "))
        assertTrue(replLines[3].toString(Charsets.UTF_8).startsWith("Message-ID: "))
        assertTrue(lines[4].toString(Charsets.UTF_8).startsWith("Date: "))
        assertTrue(replLines[4].toString(Charsets.UTF_8).startsWith("Date: "))
    }

    @org.junit.jupiter.api.Test
    fun concatRawLines() {
        val mail = getMailByteArray(makeSimpleMail())
        val lines = app.getRawLines(mail)

        val newMail = app.concatRawLines(lines)
        assertTrue(mail.contentEquals(newMail))
    }

    @org.junit.jupiter.api.Test
    fun replaceRawBytes() {
        val mail = getMailByteArray(makeSimpleMail())
        val replMailNoupdate = app.replaceRawBytes(mail, false, false)
        assertNotEquals(mail, replMailNoupdate)
        assertTrue(mail.contentEquals(replMailNoupdate))

        val replMail = app.replaceRawBytes(mail, true, true)
        assertNotEquals(mail, replMail)
        assertFalse(mail.contentEquals(replMail))
    }

    @org.junit.jupiter.api.Test
    fun getSettings() {
        val file = createTempFile()
        file.writeText(app.makeJsonSample())

        val settings = app.getSettings(file.path)
        assertEquals("172.16.3.151", settings?.smtpHost)
        assertEquals(25, settings?.smtpPort)
        assertEquals("a001@ah62.example.jp", settings?.fromAddress)
        assertTrue(listOf("a001@ah62.example.jp", "a002@ah62.example.jp", "a003@ah62.example.jp")
                == settings?.toAddress)
        assertTrue(listOf("test1.eml", "test2.eml", "test3.eml")
                == settings?.emlFile)
        assertEquals(true, settings?.updateDate)
        assertEquals(true, settings?.updateMessageId)
        assertEquals(false, settings?.useParallel)
    }

    @org.junit.jupiter.api.Test
    fun isLastReply() {
        assertFalse(app.isLastReply("250-First line"));
        assertFalse(app.isLastReply("250-Second line"));
        assertFalse(app.isLastReply("250-234 Text beginning with numbers"));
        assertTrue(app.isLastReply("250 The last line"));
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
        useSetStdout() {
            System.setOut(PrintStream(stdOutStream))
            block()
        }
        return stdOutStream.toString()
    }

    @org.junit.jupiter.api.Test
    fun sendRawBytes() {
        val file = createTempFile()
        val mail = getMailByteArray(makeSimpleMail())
        file.writeBytes(mail)

        val fileOutStream = ByteArrayOutputStream()
        val sendLine = getStdout() {
            app.sendRawBytes(fileOutStream, file.path, false, false)
        }
        assertEquals("send: ${file.absolutePath}\r\n", sendLine)
        assertTrue(mail.contentEquals(fileOutStream.toByteArray()))

        val fileOutStream2 = ByteArrayOutputStream()
        app.sendRawBytes(fileOutStream2, file.path, true, true)
        assertFalse(mail.contentEquals(fileOutStream2.toByteArray()))
    }

    @org.junit.jupiter.api.Test
    fun recvLine() {
        val recvLine = getStdout() {
            var bufReader = BufferedReader(StringReader("250 OK\r\n"))
            assertEquals("250 OK", app.recvLine(bufReader))
        }
        assertEquals("recv: 250 OK\r\n", recvLine)

        var bufReader2 = BufferedReader(StringReader(""))
        org.junit.jupiter.api.assertThrows<IOException> { app.recvLine(bufReader2) }

        var bufReader3 = BufferedReader(StringReader("554 Transaction failed\r\n"))
        org.junit.jupiter.api.assertThrows<IOException> { app.recvLine(bufReader3) }
    }

    @org.junit.jupiter.api.Test
    fun sendLine() {
        fun test(cmd: String, stdout_expected: String, writer_expected: String) {
            val strWriter = StringWriter()
            val bufWriter = BufferedWriter(strWriter)

            val sendLine = getStdout {
                app.sendLine(bufWriter, cmd)
            }
            assertEquals(stdout_expected, sendLine)
            assertEquals(writer_expected, strWriter.toString())
        }

        test("EHLO localhost", "send: EHLO localhost\r\n", "EHLO localhost\r\n")
        test("\r\n.", "send: <CRLF>.\r\n", "\r\n.\r\n")
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
    fun sendCrLfDot() {
        app.sendCrLfDot(makeTestSendCmd("\r\n."))
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
            val noKey = Regex(key).replaceFirst(json, "X-$key")
            app.checkSettings(app.getSettingsFromText(noKey)!!)
        }

        org.junit.jupiter.api.assertThrows<IOException> { checkNoKey("smtpHost") }
        org.junit.jupiter.api.assertThrows<IOException> { checkNoKey("smtpPort") }
        org.junit.jupiter.api.assertThrows<IOException> { checkNoKey("fromAddress") }
        org.junit.jupiter.api.assertThrows<IOException> { checkNoKey("toAddress") }
        org.junit.jupiter.api.assertThrows<IOException> { checkNoKey("emlFile") }
        org.junit.jupiter.api.assertDoesNotThrow { checkNoKey("testKey") }
    }
}