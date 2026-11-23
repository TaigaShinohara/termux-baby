package com.termux.app.baby;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BabyOutputFormatterTest {

    @Test
    public void stripAnsi_removesEscapeSequencesAndHandlesNullEmpty() {
        assertEquals("", BabyOutputFormatter.stripAnsi(null));
        assertEquals("", BabyOutputFormatter.stripAnsi(""));
        assertEquals("plain text", BabyOutputFormatter.stripAnsi("plain text"));
        assertEquals("hello world", BabyOutputFormatter.stripAnsi("\u001B[32mhello\u001B[0m \u001B[1;34mworld\u001B[0m"));
    }

    @Test
    public void parseFiles_handlesEmptyAndDotOnlyListings() {
        BabyOutputFormatter.ParsedSummary empty = BabyOutputFormatter.parse(
            BabyAction.OutputFormat.FILES, "", "", 0
        );
        assertEquals(BabyOutputFormatter.SummaryKind.FILES_EMPTY, empty.kind);
        assertEquals(0, empty.totalItems);

        String onlyDots = "total 8\n"
            + "drwx------ 2 u0_a1 u0_a1 4096 Jan  1 12:00 .\n"
            + "drwx------ 3 u0_a1 u0_a1 4096 Jan  1 12:00 ..\n";
        BabyOutputFormatter.ParsedSummary dotsSummary = BabyOutputFormatter.parse(
            BabyAction.OutputFormat.FILES, onlyDots, "", 0
        );
        assertEquals(BabyOutputFormatter.SummaryKind.FILES_EMPTY, dotsSummary.kind);
        assertEquals(0, dotsSummary.totalItems);
    }

    @Test
    public void parseFiles_countsDirectoriesFilesAndCapsSamplesAtSix() {
        String lsOutput = "total 32\n"
            + "drwx------ 2 u0_a1 u0_a1 4096 Jan  1 12:00 .\n"
            + "drwx------ 3 u0_a1 u0_a1 4096 Jan  1 12:00 ..\n"
            + "drwxr-xr-x 2 u0_a1 u0_a1 4096 Jan  1 12:00 notes\n"
            + "drwxr-xr-x 2 u0_a1 u0_a1 4096 Jan  1 12:00 projects\n"
            + "-rw-r--r-- 1 u0_a1 u0_a1  120 Jan  1 12:01 hello.py\n"
            + "-rw-r--r-- 1 u0_a1 u0_a1   42 Jan  1 12:02 memo 1.txt\n"
            + "-rw-r--r-- 1 u0_a1 u0_a1   10 Jan  1 12:03 a.txt\n"
            + "-rw-r--r-- 1 u0_a1 u0_a1   10 Jan  1 12:04 b.txt\n"
            + "-rw-r--r-- 1 u0_a1 u0_a1   10 Jan  1 12:05 c.txt\n";

        BabyOutputFormatter.ParsedSummary summary = BabyOutputFormatter.parse(
            BabyAction.OutputFormat.FILES, lsOutput, "", 0
        );
        assertEquals(BabyOutputFormatter.SummaryKind.FILES_LIST, summary.kind);
        assertEquals(7, summary.totalItems);
        assertEquals(2, summary.dirCount);
        assertEquals(5, summary.fileCount);
        assertEquals(6, summary.sampleItems.size());
        assertEquals("notes/", summary.sampleItems.get(0));
        assertEquals("projects/", summary.sampleItems.get(1));
        assertEquals("hello.py", summary.sampleItems.get(2));
        assertEquals("memo 1.txt", summary.sampleItems.get(3));
        assertNotNull(summary.previewText);
        assertTrue(summary.previewText.contains("• notes/"));
    }

    @Test
    public void parseNetwork_parsesOnlineRttAndOfflineFailures() {
        String pingOk = "PING 1.1.1.1 (1.1.1.1) 56(84) bytes of data.\n"
            + "64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=18.4 ms\n"
            + "64 bytes from 1.1.1.1: icmp_seq=2 ttl=57 time=21.2 ms\n"
            + "rtt min/avg/max/mdev = 18.400/19.800/21.200/1.400 ms\n";
        BabyOutputFormatter.ParsedSummary online = BabyOutputFormatter.parse(
            BabyAction.OutputFormat.NETWORK, pingOk, "", 0
        );
        assertEquals(BabyOutputFormatter.SummaryKind.NETWORK_ONLINE, online.kind);
        assertEquals("20", online.pingAvgMs);

        String pingSubTen = "64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=4.36 ms\n";
        BabyOutputFormatter.ParsedSummary fast = BabyOutputFormatter.parse(
            BabyAction.OutputFormat.NETWORK, pingSubTen, "", 0
        );
        assertEquals(BabyOutputFormatter.SummaryKind.NETWORK_ONLINE, fast.kind);
        assertEquals("4.4", fast.pingAvgMs);

        String pingFail = "3 packets transmitted, 0 received, 100% packet loss, time 2040ms\n";
        BabyOutputFormatter.ParsedSummary offline = BabyOutputFormatter.parse(
            BabyAction.OutputFormat.NETWORK, pingFail, "", 1
        );
        assertEquals(BabyOutputFormatter.SummaryKind.NETWORK_OFFLINE, offline.kind);
        assertNull(offline.pingAvgMs);
    }

    @Test
    public void parseDeviceInfo_extractsArchitectureAndAvailableSpace() {
        String deviceOutput = "aarch64\n"
            + "Filesystem      Size  Used Avail Use% Mounted on\n"
            + "/dev/block/dm-5 110G   68G   42G  62% /data\n";
        BabyOutputFormatter.ParsedSummary summary = BabyOutputFormatter.parse(
            BabyAction.OutputFormat.DEVICE_INFO, deviceOutput, "", 0
        );
        assertEquals(BabyOutputFormatter.SummaryKind.DEVICE_INFO, summary.kind);
        assertEquals("aarch64", summary.architecture);
        assertEquals("42G", summary.availableSpace);

        BabyOutputFormatter.ParsedSummary empty = BabyOutputFormatter.parse(
            BabyAction.OutputFormat.DEVICE_INFO, "", "", 0
        );
        assertEquals("-", empty.architecture);
        assertEquals("-", empty.availableSpace);
    }

    @Test
    public void parsePythonInstallAndGeneric_extractExpectedPreviews() {
        String pyOut = "Hello from Termux Baby! 2 + 2 = 4\nline2\nline3\nline4\nline5\n";
        BabyOutputFormatter.ParsedSummary py = BabyOutputFormatter.parse(
            BabyAction.OutputFormat.PYTHON, pyOut, "", 0
        );
        assertEquals(BabyOutputFormatter.SummaryKind.PYTHON_OK, py.kind);
        assertEquals("Hello from Termux Baby! 2 + 2 = 4\nline2\nline3\nline4", py.previewText);

        BabyOutputFormatter.ParsedSummary install = BabyOutputFormatter.parse(
            BabyAction.OutputFormat.INSTALL, "Setting up python...\nDone.\nExtra line\n", "", 0
        );
        assertEquals(BabyOutputFormatter.SummaryKind.INSTALL_OK, install.kind);
        assertEquals("Setting up python...\nDone.", install.previewText);

        BabyOutputFormatter.ParsedSummary genericEmpty = BabyOutputFormatter.parse(
            BabyAction.OutputFormat.GENERIC, "   \n ", "", 0
        );
        assertEquals(BabyOutputFormatter.SummaryKind.GENERIC_EMPTY, genericEmpty.kind);

        BabyOutputFormatter.ParsedSummary genericOk = BabyOutputFormatter.parse(
            BabyAction.OutputFormat.GENERIC, "\u001B[36m/data/data/com.termux/files/home\u001B[0m\n", "", 0
        );
        assertEquals(BabyOutputFormatter.SummaryKind.GENERIC_OK, genericOk.kind);
        assertEquals("/data/data/com.termux/files/home", genericOk.previewText);
    }
}
