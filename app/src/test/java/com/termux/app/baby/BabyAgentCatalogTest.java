package com.termux.app.baby;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Guards the one-tap CLI agent catalog: the four agents the user asked for
 * (opencode, Claude Code, Gemini CLI, Antigravity agy), native routes first,
 * proot only for agy, and every install command safe for the Baby runner.
 */
public class BabyAgentCatalogTest {

    private static BabyActionCatalog.AgentSpec byId(String id) {
        for (BabyActionCatalog.AgentSpec agent : BabyActionCatalog.getInstallableAgents()) {
            if (agent.id.equals(id)) return agent;
        }
        throw new AssertionError("missing agent " + id);
    }

    @Test
    public void catalog_hasFourAgentsWithUniqueIdsAndBinaries() {
        List<BabyActionCatalog.AgentSpec> agents = BabyActionCatalog.getInstallableAgents();
        assertEquals(4, agents.size());

        Set<String> ids = new HashSet<>();
        Set<String> binaries = new HashSet<>();
        for (BabyActionCatalog.AgentSpec agent : agents) {
            ids.add(agent.id);
            binaries.add(agent.binaryName);
            assertFalse(agent.installCommand.trim().isEmpty());
            assertFalse(agent.launchCommand.trim().isEmpty());
        }
        assertEquals(4, ids.size());
        assertEquals(4, binaries.size());
    }

    @Test
    public void catalog_nativeRoutesFirstProotLast() {
        List<BabyActionCatalog.AgentSpec> agents = BabyActionCatalog.getInstallableAgents();
        assertEquals(BabyActionCatalog.AGENT_OPENCODE, agents.get(0).id);
        assertEquals(BabyActionCatalog.AGENT_AGY, agents.get(agents.size() - 1).id);
        // Only the agy route may use proot.
        for (int i = 0; i < agents.size() - 1; i++) {
            assertFalse(agents.get(i).installCommand.contains("proot"));
        }
        assertTrue(agents.get(agents.size() - 1).installCommand.contains("proot-distro"));
    }

    @Test
    public void opencode_usesCommunityTermuxRepoInstaller() {
        BabyActionCatalog.AgentSpec opencode = byId(BabyActionCatalog.AGENT_OPENCODE);
        assertEquals("opencode", opencode.binaryName);
        assertTrue(opencode.installCommand.contains("hope2333.github.io/repo/install.sh"));
        assertTrue(opencode.installCommand.contains("--install opencode"));
        assertEquals("opencode", opencode.launchCommand);
    }

    @Test
    public void claude_installsViaNpmWithSystemRipgrep() {
        BabyActionCatalog.AgentSpec claude = byId(BabyActionCatalog.AGENT_CLAUDE);
        assertEquals("claude", claude.binaryName);
        assertTrue(claude.installCommand.contains("nodejs-lts"));
        assertTrue(claude.installCommand.contains("npm install -g @anthropic-ai/claude-code"));
        assertTrue(claude.installCommand.contains("ripgrep"));
        assertTrue(claude.launchCommand.contains("USE_BUILTIN_RIPGREP=0"));
        assertTrue(claude.launchCommand.contains("claude"));
    }

    @Test
    public void gemini_installsViaNpm() {
        BabyActionCatalog.AgentSpec gemini = byId(BabyActionCatalog.AGENT_GEMINI);
        assertEquals("gemini", gemini.binaryName);
        assertTrue(gemini.installCommand.contains("nodejs-lts"));
        assertTrue(gemini.installCommand.contains("npm install -g @google/gemini-cli"));
        assertEquals("gemini", gemini.launchCommand);
    }

    @Test
    public void agy_installsOfficialCliInsideDebianAndCreatesWrapper() {
        BabyActionCatalog.AgentSpec agy = byId(BabyActionCatalog.AGENT_AGY);
        assertEquals("agy", agy.binaryName);
        String cmd = agy.installCommand;
        assertTrue(cmd.contains("proot-distro install debian"));
        assertTrue(cmd.contains("https://antigravity.google/cli/install.sh"));
        // The wrapper lands in $PREFIX/bin so the installed check and launch work.
        assertTrue(cmd.contains("$PREFIX/bin/agy"));
        assertTrue(cmd.contains("/root/.local/bin/agy"));
        assertEquals("agy", agy.launchCommand);
    }

    @Test
    public void allInstallCommands_passBabySafetyGuard() {
        for (BabyActionCatalog.AgentSpec agent : BabyActionCatalog.getInstallableAgents()) {
            assertTrue("install command blocked by safety guard: " + agent.id,
                BabyActionCatalog.isSafeForAutoRun(agent.installCommand));
        }
    }
}
