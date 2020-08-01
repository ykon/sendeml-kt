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

        assertEquals(390, indices[indices.size - 3])
        assertEquals(415, indices[indices.size - 2])
        assertEquals(417, indices[indices.size - 1])
    }

    @org.junit.jupiter.api.Test
    fun copyNew() {
        val mail = getMailByteArray(makeSimpleMail())
        val buf = app.copyNew(mail, 0, 10)

        assertEquals(10, buf.size)
        assertEquals(mail[0], buf[0])
        assertEquals(mail[1], buf[1])
        assertEquals(mail[2], buf[2])

        assertEquals(mail[buf.size - 3], buf[buf.size - 3])
        assertEquals(mail[buf.size - 2], buf[buf.size - 2])
        assertEquals(mail[buf.size - 1], buf[buf.size - 1])

        buf[0] = 0
        assertEquals(0, buf[0])
        assertNotEquals(0, mail[0])
    }

    @org.junit.jupiter.api.Test
    fun getRawLines() {
        val mail = getMailByteArray(makeSimpleMail())
        val lines = app.getRawLines(mail)

        assertEquals(13, lines.size)
        assertEquals("From: a001 <a001@ah62.example.jp>\r\n", lines[0].toString(Charsets.UTF_8))
        assertEquals("Subject: test\r\n", lines[1].toString(Charsets.UTF_8))
        assertEquals("To: a002@ah62.example.jp\r\n", lines[2].toString(Charsets.UTF_8))

        assertEquals("Content-Language: en-US\r\n", lines[lines.size - 3].toString(Charsets.UTF_8))
        assertEquals("\r\n", lines[lines.size - 2].toString(Charsets.UTF_8))
        assertEquals("test", lines[lines.size - 1].toString(Charsets.UTF_8))
    }

    @org.junit.jupiter.api.Test
    fun isDateLine() {
        assertTrue(app.isDateLine("Date: xxx"))
        assertFalse(app.isDateLine("xxx: Date"))
        assertFalse(app.isDateLine("X-Date: xxx"))
    }

    @org.junit.jupiter.api.Test
    fun makeNowDateLine() {
        val line = app.makeNowDateLine()
        assertTrue(line.startsWith("Date: "))
        assertTrue(line.length <= 76)
    }

    @org.junit.jupiter.api.Test
    fun isMessageIdLine() {
        assertTrue(app.isMessageIdLine("Message-ID: xxx"))
        assertFalse(app.isMessageIdLine("xxx: Message-ID"))
        assertFalse(app.isMessageIdLine("X-Message-ID xxx"))
    }

    @org.junit.jupiter.api.Test
    fun makeRandomMessageIdLine() {
        val line = app.makeRandomMessageIdLine()
        assertTrue(line.startsWith("Message-ID: "))
        assertTrue(line.length <= 76)
    }

    private fun makeByteArray(vararg cs: Char): ByteArray {
        return cs.map { it.toByte() }.toByteArray()
    }

    @org.junit.jupiter.api.Test
    fun isFirstD() {
        assertTrue(app.isFirstD(makeByteArray('D', 'A', 'B')))
        assertTrue(app.isFirstD(makeByteArray('D', 'B', 'A')))
        assertFalse(app.isFirstD(makeByteArray('A', 'B', 'D')))
        assertFalse(app.isFirstD(makeByteArray('B', 'A', 'D')))
    }

    @org.junit.jupiter.api.Test
    fun isFirstM() {
        assertTrue(app.isFirstM(makeByteArray('M', 'A', 'B')))
        assertTrue(app.isFirstM(makeByteArray('M', 'B', 'A')))
        assertFalse(app.isFirstM(makeByteArray('A', 'B', 'M')))
        assertFalse(app.isFirstM(makeByteArray('B', 'A', 'M')))
    }

    @org.junit.jupiter.api.Test
    fun findDateLineIndex() {
        val mail = getMailByteArray(makeSimpleMail())
        val lines = app.getRawLines(mail)
        assertEquals(4, app.findDateLineIndex(lines))

        val mutLines = lines.toMutableList()
        mutLines.removeAt(4)
        assertEquals(-1, app.findDateLineIndex(mutLines))
    }

    @org.junit.jupiter.api.Test
    fun findMessageIdLineIndex() {
        val mail = getMailByteArray(makeSimpleMail())
        val lines = app.getRawLines(mail)
        assertEquals(3, app.findMessageIdLineIndex(lines))

        val mutLines = lines.toMutableList()
        mutLines.removeAt(3)
        assertEquals(-1, app.findMessageIdLineIndex(mutLines))
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
    fun isSuccess() {
        assertTrue(app.isSuccess("200 xxx"))
        assertTrue(app.isSuccess("300 xxx"))
        assertFalse(app.isSuccess("400 xxx"))
        assertFalse(app.isSuccess("500 xxx"))
        assertFalse(app.isSuccess("xxx 200"))
        assertFalse(app.isSuccess("xxx 300"))
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
}