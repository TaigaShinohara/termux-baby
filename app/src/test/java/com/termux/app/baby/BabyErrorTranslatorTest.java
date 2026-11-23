package com.termux.app.baby;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BabyErrorTranslatorTest {

    @Test
    public void classify_commandNotFound_extractsBinaryAndMapsPackage() {
        BabyErrorTranslator.TranslatedError pyErr = BabyErrorTranslator.classify(
            127, "", "sh: python3: not found", false, false
        );
        assertEquals(BabyErrorTranslator.ErrorCategory.COMMAND_NOT_FOUND, pyErr.category);
        assertEquals("python3", pyErr.missingCommand);
        assertEquals("python", pyErr.suggestedPackage);
        assertTrue(pyErr.hasInstallAction());

        BabyErrorTranslator.TranslatedError nodeErr = BabyErrorTranslator.classify(
            127, "", "bash: node: command not found", false, false
        );
        assertEquals(BabyErrorTranslator.ErrorCategory.COMMAND_NOT_FOUND, nodeErr.category);
        assertEquals("node", nodeErr.missingCommand);
        assertEquals("nodejs", nodeErr.suggestedPackage);
        assertTrue(nodeErr.hasInstallAction());

        BabyErrorTranslator.TranslatedError unknownBin = BabyErrorTranslator.classify(
            127, "", "sh: customtool: not found", false, false
        );
        assertEquals(BabyErrorTranslator.ErrorCategory.COMMAND_NOT_FOUND, unknownBin.category);
        assertEquals("customtool", unknownBin.missingCommand);
        assertNull(unknownBin.suggestedPackage);
        assertFalse(unknownBin.hasInstallAction());
    }

    @Test
    public void mapBinaryToPackage_coversKnownAndUnknownBinaries() {
        assertEquals("python", BabyErrorTranslator.mapBinaryToPackage("python"));
        assertEquals("python", BabyErrorTranslator.mapBinaryToPackage("PYTHON3"));
        assertEquals("python", BabyErrorTranslator.mapBinaryToPackage("pip"));
        assertEquals("git", BabyErrorTranslator.mapBinaryToPackage("git"));
        assertEquals("nano", BabyErrorTranslator.mapBinaryToPackage("nano"));
        assertEquals("tree", BabyErrorTranslator.mapBinaryToPackage("tree"));
        assertEquals("curl", BabyErrorTranslator.mapBinaryToPackage("curl"));
        assertEquals("wget", BabyErrorTranslator.mapBinaryToPackage("wget"));
        assertEquals("nodejs", BabyErrorTranslator.mapBinaryToPackage("node"));
        assertEquals("vim", BabyErrorTranslator.mapBinaryToPackage("vim"));
        assertNull(BabyErrorTranslator.mapBinaryToPackage("nonexistent_bin"));
    }

    @Test
    public void classify_networkPermissionFileNotFoundTimeoutAndCancelled() {
        assertEquals(
            BabyErrorTranslator.ErrorCategory.NETWORK_UNREACHABLE,
            BabyErrorTranslator.classify(1, "", "ping: Temporary failure resolving 'example.com'", false, false).category
        );
        assertEquals(
            BabyErrorTranslator.ErrorCategory.NETWORK_UNREACHABLE,
            BabyErrorTranslator.classify(1, "3 transmitted, 0 received, 100% packet loss", "", false, false).category
        );
        assertEquals(
            BabyErrorTranslator.ErrorCategory.PERMISSION_DENIED,
            BabyErrorTranslator.classify(1, "", "ls: cannot open directory '/root': Permission denied", false, false).category
        );
        assertEquals(
            BabyErrorTranslator.ErrorCategory.FILE_NOT_FOUND,
            BabyErrorTranslator.classify(2, "", "cat: missing.txt: No such file or directory", false, false).category
        );
        assertEquals(
            BabyErrorTranslator.ErrorCategory.TIMEOUT,
            BabyErrorTranslator.classify(124, "", "", true, false).category
        );
        assertEquals(
            BabyErrorTranslator.ErrorCategory.CANCELLED,
            BabyErrorTranslator.classify(130, "", "", true, true).category
        );
    }

    @Test
    public void classify_bootstrapNotReadyAndUnknown() {
        BabyCommandExecutor.ExecutionResult missingBootstrap = new BabyCommandExecutor.ExecutionResult(
            "ls", 1, "", "Bootstrap not ready", false, false, true, 5L
        );
        BabyErrorTranslator.TranslatedError bootstrapErr = BabyErrorTranslator.classify(missingBootstrap);
        assertEquals(BabyErrorTranslator.ErrorCategory.BOOTSTRAP_NOT_READY, bootstrapErr.category);
        assertFalse(bootstrapErr.hasInstallAction());

        BabyErrorTranslator.TranslatedError unknownErr = BabyErrorTranslator.classify(
            1, "", "some unexpected error", false, false
        );
        assertEquals(BabyErrorTranslator.ErrorCategory.UNKNOWN, unknownErr.category);
        assertFalse(unknownErr.hasInstallAction());
    }
}
