package com.termux.app.baby;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Catalog of the 6 core Baby Home action cards, Command Helper recipes, installable beginner
 * tools, and pure-Java shell-escaping / safety utilities.
 */
public final class BabyActionCatalog {

    public static final String ACTION_SEE_FILES = "see_files";
    public static final String ACTION_INSTALL_TOOLS = "install_tools";
    public static final String ACTION_CHECK_NETWORK = "check_network";
    public static final String ACTION_QUICK_NOTE = "quick_note";
    public static final String ACTION_DEVICE_INFO = "device_info";
    public static final String ACTION_TRY_PYTHON = "try_python";

    public static final String PLACEHOLDER_ARG = "{arg}";
    public static final String PLACEHOLDER_GLOB_ARG = "{glob_arg}";

    private static final String SAFE_PYTHON_CALC_TEMPLATE =
        "python3 -c 'import ast, operator as op, sys\n"
            + "ops={ast.Add:op.add,ast.Sub:op.sub,ast.Mult:op.mul,ast.Div:op.truediv,"
            + "ast.FloorDiv:op.floordiv,ast.Mod:op.mod,ast.Pow:op.pow,ast.USub:op.neg,ast.UAdd:op.pos}\n"
            + "def ev(n):\n"
            + " if isinstance(n,ast.Expression): return ev(n.body)\n"
            + " if isinstance(n,ast.Constant) and isinstance(n.value,(int,float)): return n.value\n"
            + " if isinstance(n,ast.BinOp) and type(n.op) in ops: return ops[type(n.op)](ev(n.left),ev(n.right))\n"
            + " if isinstance(n,ast.UnaryOp) and type(n.op) in ops: return ops[type(n.op)](ev(n.operand))\n"
            + " raise ValueError(\"Please enter numbers and + - * / only\")\n"
            + "print(ev(ast.parse(sys.argv[1], mode=\"eval\")))' " + PLACEHOLDER_ARG;

    public static final String PYTHON_HELLO_COMMAND =
        "python3 -c 'print(\"Hello from Termux Baby! 2 + 2 =\", 2 + 2)'";

    /**
     * Curated beginner-friendly package specification for the Tool Installer sheet.
     */
    public static final class ToolSpec {
        @NonNull
        public final String packageName;
        @NonNull
        public final String binaryName;
        @StringRes
        public final int nameResId;
        @StringRes
        public final int descResId;
        @DrawableRes
        public final int iconResId;
        @Nullable
        public final String tryCommand;
        @NonNull
        public final BabyAction.OutputFormat tryOutputFormat;

        public ToolSpec(
            @NonNull String packageName,
            @NonNull String binaryName,
            @StringRes int nameResId,
            @StringRes int descResId,
            @DrawableRes int iconResId,
            @Nullable String tryCommand,
            @NonNull BabyAction.OutputFormat tryOutputFormat
        ) {
            this.packageName = packageName;
            this.binaryName = binaryName;
            this.nameResId = nameResId;
            this.descResId = descResId;
            this.iconResId = iconResId;
            this.tryCommand = tryCommand;
            this.tryOutputFormat = tryOutputFormat;
        }

        @NonNull
        public String buildInstallCommand() {
            return buildPackageInstallCommand(packageName);
        }
    }

    private BabyActionCatalog() {
    }

    /**
     * Returns the 6 primary one-tap action cards shown on Baby Mode Home (2 columns x 3 rows).
     */
    @NonNull
    public static List<BabyAction> getHomeActions() {
        return Collections.unmodifiableList(Arrays.asList(
            new BabyAction(
                ACTION_SEE_FILES,
                R.string.baby_action_files_title,
                R.string.baby_action_files_sub,
                R.drawable.ic_baby_files,
                BabyAction.ActionType.DIRECT_COMMAND,
                BabyAction.Category.FILES,
                "ls -la --color=never",
                BabyAction.OutputFormat.FILES,
                0,
                0,
                BabyCommandExecutor.DEFAULT_TIMEOUT_SECONDS
            ),
            new BabyAction(
                ACTION_INSTALL_TOOLS,
                R.string.baby_action_install_title,
                R.string.baby_action_install_sub,
                R.drawable.ic_baby_install,
                BabyAction.ActionType.TOOL_INSTALLER,
                BabyAction.Category.TOOLS,
                null,
                BabyAction.OutputFormat.INSTALL,
                0,
                0,
                BabyCommandExecutor.INSTALL_TIMEOUT_SECONDS
            ),
            new BabyAction(
                ACTION_CHECK_NETWORK,
                R.string.baby_action_network_title,
                R.string.baby_action_network_sub,
                R.drawable.ic_baby_network,
                BabyAction.ActionType.DIRECT_COMMAND,
                BabyAction.Category.NETWORK,
                "ping -c 3 -W 4 1.1.1.1",
                BabyAction.OutputFormat.NETWORK,
                0,
                0,
                15
            ),
            new BabyAction(
                ACTION_QUICK_NOTE,
                R.string.baby_action_note_title,
                R.string.baby_action_note_sub,
                R.drawable.ic_baby_note,
                BabyAction.ActionType.QUICK_NOTE,
                BabyAction.Category.FILES,
                null,
                BabyAction.OutputFormat.GENERIC,
                0,
                0,
                BabyCommandExecutor.DEFAULT_TIMEOUT_SECONDS
            ),
            new BabyAction(
                ACTION_DEVICE_INFO,
                R.string.baby_action_device_title,
                R.string.baby_action_device_sub,
                R.drawable.ic_baby_device,
                BabyAction.ActionType.DIRECT_COMMAND,
                BabyAction.Category.TOOLS,
                "uname -m && df -h \"$HOME\"",
                BabyAction.OutputFormat.DEVICE_INFO,
                0,
                0,
                BabyCommandExecutor.DEFAULT_TIMEOUT_SECONDS
            ),
            new BabyAction(
                ACTION_TRY_PYTHON,
                R.string.baby_action_python_title,
                R.string.baby_action_python_sub,
                R.drawable.ic_baby_python,
                BabyAction.ActionType.COMMAND_WITH_INPUT,
                BabyAction.Category.LEARN,
                SAFE_PYTHON_CALC_TEMPLATE,
                BabyAction.OutputFormat.PYTHON,
                R.string.baby_recipe_python_calc_title,
                R.string.baby_recipe_python_calc_hint,
                BabyCommandExecutor.DEFAULT_TIMEOUT_SECONDS
            )
        ));
    }

    /**
     * Learning order for the "next to learn" home slot and the catalog:
     * place → list → make/find → time → network → device → updates → Python.
     */
    @NonNull
    public static List<String> getCurriculumIds() {
        return Collections.unmodifiableList(Arrays.asList(
            "recipe_pwd",
            ACTION_SEE_FILES,
            "recipe_mkdir",
            "recipe_find",
            "recipe_date",
            ACTION_CHECK_NETWORK,
            "recipe_ip",
            ACTION_DEVICE_INFO,
            "recipe_update",
            ACTION_TRY_PYTHON
        ));
    }

    /** Finds a home action or helper recipe by id, or null. */
    @Nullable
    public static BabyAction findById(@Nullable String id) {
        if (id == null) return null;
        for (BabyAction action : getHomeActions()) {
            if (action.id.equals(id)) return action;
        }
        for (BabyAction recipe : getHelperRecipes()) {
            if (recipe.id.equals(id)) return recipe;
        }
        return null;
    }

    /**
     * Plain-language breakdown of a command's parts, keyed by action id —
     * the "what does this piece do?" learning material shown next to every
     * command. Empty for actions without a fixed command.
     */
    @NonNull
    public static List<BabyAction.TokenNote> getTokenNotes(@NonNull String actionId) {
        switch (actionId) {
            case ACTION_SEE_FILES:
                return Arrays.asList(
                    new BabyAction.TokenNote("ls", R.string.baby_token_ls),
                    new BabyAction.TokenNote("-la", R.string.baby_token_ls_la),
                    new BabyAction.TokenNote("--color=never", R.string.baby_token_color_never));
            case ACTION_CHECK_NETWORK:
                return Arrays.asList(
                    new BabyAction.TokenNote("ping", R.string.baby_token_ping),
                    new BabyAction.TokenNote("-c 3", R.string.baby_token_ping_c),
                    new BabyAction.TokenNote("-W 4", R.string.baby_token_ping_w),
                    new BabyAction.TokenNote("1.1.1.1", R.string.baby_token_ping_target));
            case ACTION_DEVICE_INFO:
                return Arrays.asList(
                    new BabyAction.TokenNote("uname -m", R.string.baby_token_uname),
                    new BabyAction.TokenNote("&&", R.string.baby_token_and),
                    new BabyAction.TokenNote("df -h", R.string.baby_token_df),
                    new BabyAction.TokenNote("\"$HOME\"", R.string.baby_token_home));
            case ACTION_TRY_PYTHON:
            case "recipe_python_calc":
                return Arrays.asList(
                    new BabyAction.TokenNote("python3", R.string.baby_token_python3),
                    new BabyAction.TokenNote("-c", R.string.baby_token_python_c),
                    new BabyAction.TokenNote("'print(式)'", R.string.baby_token_python_expr));
            case "recipe_pwd":
                return Collections.singletonList(
                    new BabyAction.TokenNote("pwd", R.string.baby_token_pwd));
            case "recipe_mkdir":
                return Arrays.asList(
                    new BabyAction.TokenNote("mkdir", R.string.baby_token_mkdir),
                    new BabyAction.TokenNote("-p", R.string.baby_token_mkdir_p),
                    new BabyAction.TokenNote("名前", R.string.baby_token_your_name));
            case "recipe_find":
                return Arrays.asList(
                    new BabyAction.TokenNote("find .", R.string.baby_token_find),
                    new BabyAction.TokenNote("-maxdepth 3", R.string.baby_token_find_depth),
                    new BabyAction.TokenNote("-iname", R.string.baby_token_find_iname),
                    new BabyAction.TokenNote("名前", R.string.baby_token_your_name));
            case "recipe_date":
                return Collections.singletonList(
                    new BabyAction.TokenNote("date", R.string.baby_token_date));
            case "recipe_ip":
                return Arrays.asList(
                    new BabyAction.TokenNote("curl", R.string.baby_token_curl),
                    new BabyAction.TokenNote("-s", R.string.baby_token_curl_s),
                    new BabyAction.TokenNote("--max-time 8", R.string.baby_token_curl_time),
                    new BabyAction.TokenNote("ifconfig.me", R.string.baby_token_ifconfig));
            case "recipe_update":
                return Arrays.asList(
                    new BabyAction.TokenNote("pkg", R.string.baby_token_pkg),
                    new BabyAction.TokenNote("update", R.string.baby_token_pkg_update),
                    new BabyAction.TokenNote("-y", R.string.baby_token_yes));
            default:
                return Collections.emptyList();
        }
    }

    /**
     * Returns plain-language recipes for the "Find an action" Command Helper sheet.
     */
    @NonNull
    public static List<BabyAction> getHelperRecipes() {
        List<BabyAction> recipes = new ArrayList<>();
        recipes.add(new BabyAction(
            "recipe_pwd",
            R.string.baby_recipe_pwd_title,
            R.string.baby_recipe_pwd_sub,
            R.drawable.ic_baby_files,
            BabyAction.ActionType.DIRECT_COMMAND,
            BabyAction.Category.FILES,
            "pwd",
            BabyAction.OutputFormat.GENERIC,
            0,
            0,
            BabyCommandExecutor.DEFAULT_TIMEOUT_SECONDS
        ));
        recipes.add(new BabyAction(
            "recipe_mkdir",
            R.string.baby_recipe_mkdir_title,
            R.string.baby_recipe_mkdir_sub,
            R.drawable.ic_baby_files,
            BabyAction.ActionType.COMMAND_WITH_INPUT,
            BabyAction.Category.FILES,
            "mkdir -p -- " + PLACEHOLDER_ARG + " && ls -la --color=never",
            BabyAction.OutputFormat.FILES,
            R.string.baby_recipe_mkdir_title,
            R.string.baby_recipe_mkdir_hint,
            BabyCommandExecutor.DEFAULT_TIMEOUT_SECONDS
        ));
        recipes.add(new BabyAction(
            "recipe_find",
            R.string.baby_recipe_find_title,
            R.string.baby_recipe_find_sub,
            R.drawable.ic_baby_search,
            BabyAction.ActionType.COMMAND_WITH_INPUT,
            BabyAction.Category.FILES,
            "find . -maxdepth 3 -iname " + PLACEHOLDER_GLOB_ARG,
            BabyAction.OutputFormat.GENERIC,
            R.string.baby_recipe_find_title,
            R.string.baby_recipe_find_hint,
            BabyCommandExecutor.DEFAULT_TIMEOUT_SECONDS
        ));
        // (No recipe_disk: it duplicated the device_info home card exactly.)
        recipes.add(new BabyAction(
            "recipe_date",
            R.string.baby_recipe_date_title,
            R.string.baby_recipe_date_sub,
            R.drawable.ic_baby_device,
            BabyAction.ActionType.DIRECT_COMMAND,
            BabyAction.Category.LEARN,
            "date",
            BabyAction.OutputFormat.GENERIC,
            0,
            0,
            BabyCommandExecutor.DEFAULT_TIMEOUT_SECONDS
        ));
        recipes.add(new BabyAction(
            "recipe_ip",
            R.string.baby_recipe_ip_title,
            R.string.baby_recipe_ip_sub,
            R.drawable.ic_baby_network,
            BabyAction.ActionType.DIRECT_COMMAND,
            BabyAction.Category.NETWORK,
            "curl -s --max-time 8 https://ifconfig.me",
            BabyAction.OutputFormat.GENERIC,
            0,
            0,
            12
        ));
        recipes.add(new BabyAction(
            "recipe_update",
            R.string.baby_recipe_update_title,
            R.string.baby_recipe_update_sub,
            R.drawable.ic_baby_install,
            BabyAction.ActionType.DIRECT_COMMAND,
            BabyAction.Category.TOOLS,
            "pkg update -y",
            BabyAction.OutputFormat.GENERIC,
            0,
            0,
            BabyCommandExecutor.INSTALL_TIMEOUT_SECONDS
        ));
        recipes.add(new BabyAction(
            "recipe_python_calc",
            R.string.baby_recipe_python_calc_title,
            R.string.baby_recipe_python_calc_sub,
            R.drawable.ic_baby_python,
            BabyAction.ActionType.COMMAND_WITH_INPUT,
            BabyAction.Category.LEARN,
            SAFE_PYTHON_CALC_TEMPLATE,
            BabyAction.OutputFormat.PYTHON,
            R.string.baby_recipe_python_calc_title,
            R.string.baby_recipe_python_calc_hint,
            BabyCommandExecutor.DEFAULT_TIMEOUT_SECONDS
        ));
        return Collections.unmodifiableList(recipes);
    }

    /**
     * Returns curated beginner-friendly tools for the Tool Installer sheet.
     */
    @NonNull
    public static List<ToolSpec> getInstallableTools() {
        return Collections.unmodifiableList(Arrays.asList(
            new ToolSpec(
                "python",
                "python3",
                R.string.baby_tool_python_name,
                R.string.baby_tool_python_desc,
                R.drawable.ic_baby_python,
                PYTHON_HELLO_COMMAND,
                BabyAction.OutputFormat.PYTHON
            ),
            new ToolSpec(
                "git",
                "git",
                R.string.baby_tool_git_name,
                R.string.baby_tool_git_desc,
                R.drawable.ic_baby_install,
                "git --version",
                BabyAction.OutputFormat.GENERIC
            ),
            new ToolSpec(
                "nano",
                "nano",
                R.string.baby_tool_nano_name,
                R.string.baby_tool_nano_desc,
                R.drawable.ic_baby_note,
                "nano --version | head -n 1",
                BabyAction.OutputFormat.GENERIC
            ),
            new ToolSpec(
                "tree",
                "tree",
                R.string.baby_tool_tree_name,
                R.string.baby_tool_tree_desc,
                R.drawable.ic_baby_files,
                "tree -L 2",
                BabyAction.OutputFormat.GENERIC
            )
        ));
    }

    /**
     * Specification for a CLI AI agent that can be installed with one tap from the
     * Tool Installer sheet and then launched into the Pro Terminal.
     *
     * <p>Install commands run through {@link BabyCommandExecutor} (background, streamed
     * output, long timeout). Launch commands are interactive TUIs, so they are sent to
     * the Pro Terminal session instead of the background executor.</p>
     */
    public static final class AgentSpec {
        @NonNull
        public final String id;
        @StringRes
        public final int nameResId;
        @StringRes
        public final int descResId;
        @DrawableRes
        public final int iconResId;
        /** Binary (or wrapper script) name in $PREFIX/bin used for the installed check. */
        @NonNull
        public final String binaryName;
        @NonNull
        public final String installCommand;
        @NonNull
        public final String launchCommand;

        public AgentSpec(
            @NonNull String id,
            @StringRes int nameResId,
            @StringRes int descResId,
            @DrawableRes int iconResId,
            @NonNull String binaryName,
            @NonNull String installCommand,
            @NonNull String launchCommand
        ) {
            this.id = id;
            this.nameResId = nameResId;
            this.descResId = descResId;
            this.iconResId = iconResId;
            this.binaryName = binaryName;
            this.installCommand = installCommand;
            this.launchCommand = launchCommand;
        }
    }

    public static final String AGENT_OPENCODE = "opencode";
    public static final String AGENT_CLAUDE = "claude";
    public static final String AGENT_GEMINI = "gemini";
    public static final String AGENT_AGY = "agy";

    /** Shared one-time setup for the npm-based agents: Node.js LTS + build tools for native modules. */
    private static final String NPM_AGENT_SETUP =
        "pkg install -y nodejs-lts python make clang";

    /**
     * Install command for Antigravity CLI ({@code agy}). The official agy binary is a
     * glibc Linux build that cannot run on Termux's bionic libc directly, so this
     * installs a Debian environment with proot-distro, runs Google's official
     * installer inside it, and drops an {@code agy} wrapper into $PREFIX/bin that
     * enters Debian and forwards all arguments.
     */
    public static final String AGY_INSTALL_COMMAND =
        "pkg install -y proot-distro"
            + " && (proot-distro install debian || true)"
            + " && proot-distro login debian -- bash -lc \"apt-get update"
            + " && DEBIAN_FRONTEND=noninteractive apt-get install -y curl ca-certificates"
            + " && curl -fsSL https://antigravity.google/cli/install.sh | bash\""
            + " && printf '%s\\n' '#!/data/data/com.termux/files/usr/bin/sh'"
            + " 'exec proot-distro login debian -- /root/.local/bin/agy \"$@\"'"
            + " > \"$PREFIX/bin/agy\""
            + " && chmod +x \"$PREFIX/bin/agy\""
            + " && echo 'Antigravity (agy) is ready. Tap Open to start.'";

    /**
     * Returns the CLI AI agents offered in the Tool Installer sheet.
     * Native Termux routes come first; Antigravity (proot) is last.
     */
    @NonNull
    public static List<AgentSpec> getInstallableAgents() {
        return Collections.unmodifiableList(Arrays.asList(
            new AgentSpec(
                AGENT_OPENCODE,
                R.string.baby_agent_opencode_name,
                R.string.baby_agent_opencode_desc,
                R.drawable.ic_baby_terminal,
                "opencode",
                // Community-maintained native bionic build for Termux, distributed
                // through its own apt repository so `pkg upgrade` keeps it current.
                "curl -fsSL https://hope2333.github.io/repo/install.sh | sh -s -- --install opencode",
                "opencode"
            ),
            new AgentSpec(
                AGENT_CLAUDE,
                R.string.baby_agent_claude_name,
                R.string.baby_agent_claude_desc,
                R.drawable.ic_baby_sparkle,
                "claude",
                NPM_AGENT_SETUP + " ripgrep && npm install -g @anthropic-ai/claude-code",
                // Prefer the Termux ripgrep package over the bundled glibc binary.
                "USE_BUILTIN_RIPGREP=0 claude"
            ),
            new AgentSpec(
                AGENT_GEMINI,
                R.string.baby_agent_gemini_name,
                R.string.baby_agent_gemini_desc,
                R.drawable.ic_baby_explore,
                "gemini",
                NPM_AGENT_SETUP + " && npm install -g @google/gemini-cli",
                "gemini"
            ),
            new AgentSpec(
                AGENT_AGY,
                R.string.baby_agent_agy_name,
                R.string.baby_agent_agy_desc,
                R.drawable.ic_baby_cards,
                "agy",
                AGY_INSTALL_COMMAND,
                "agy"
            )
        ));
    }

    /**
     * Notes for a raw command string (installs, agent setups) that has no
     * catalog action id: keyed by the leading program, so a {@code pkg
     * install} run explains its parts just like a catalog recipe does.
     */
    @NonNull
    public static List<BabyAction.TokenNote> getTokenNotesForCommand(@Nullable String command) {
        if (command == null) return Collections.emptyList();
        String c = command.trim();
        if (c.startsWith("pkg install")) {
            return Arrays.asList(
                new BabyAction.TokenNote("pkg", R.string.baby_token_pkg),
                new BabyAction.TokenNote("install", R.string.baby_token_pkg_install),
                new BabyAction.TokenNote("-y", R.string.baby_token_yes));
        }
        if (c.startsWith("pkg update") || c.startsWith("pkg upgrade")) {
            return getTokenNotes("recipe_update");
        }
        if (c.startsWith("python3")) {
            return Arrays.asList(
                new BabyAction.TokenNote("python3", R.string.baby_token_python3),
                new BabyAction.TokenNote("-c", R.string.baby_token_python_c),
                new BabyAction.TokenNote("'print(式)'", R.string.baby_token_python_expr));
        }
        if (c.startsWith("curl")) {
            return Collections.singletonList(
                new BabyAction.TokenNote("curl", R.string.baby_token_curl));
        }
        if (c.startsWith("npm")) {
            return Collections.singletonList(
                new BabyAction.TokenNote("npm", R.string.baby_token_npm));
        }
        return Collections.emptyList();
    }

    /** The teaching form of the Python calculator: what the user would type. */
    public static final String PYTHON_CALC_DISPLAY_FORM = "python3 -c 'print(式)'";

    private static boolean isPythonCalcTemplate(@Nullable String template) {
        return SAFE_PYTHON_CALC_TEMPLATE.equals(template);
    }

    /**
     * Returns a short, readable command preview for display in Command Helper cards.
     */
    @NonNull
    public static String getDisplayCommandPreview(@NonNull BabyAction action) {
        if (action.commandTemplate == null) {
            return "";
        }
        // The Python calculator's executed command is a long safe-evaluator
        // script — an implementation detail, not something to learn. Its
        // display form is the equivalent command the user would type.
        if (isPythonCalcTemplate(action.commandTemplate)) {
            return PYTHON_CALC_DISPLAY_FORM;
        }
        // Always the REAL command — a shortened fake preview would teach a
        // command that does not exist. Placeholders render as the word the
        // learner will type (名前), which the token notes also use. Long
        // templates are honestly cut with an ellipsis at a visible boundary.
        String preview = action.commandTemplate
            .replace(PLACEHOLDER_ARG, "名前")
            .replace(PLACEHOLDER_GLOB_ARG, "'*名前*'");
        return truncateForDisplay(preview);
    }

    /**
     * The display form of a command built from a template + user input:
     * the teaching form for the Python calculator, the real resolved
     * command for everything else.
     */
    @NonNull
    public static String buildDisplayCommand(@NonNull String template, @Nullable String userArg) {
        if (isPythonCalcTemplate(template)) {
            String expr = userArg != null ? userArg.trim() : "";
            return "python3 -c 'print(" + (expr.isEmpty() ? "式" : expr) + ")'";
        }
        return buildSafeCommand(template, userArg);
    }

    /**
     * The display form of an already-resolved (executed) command: for the
     * Python calculator, the expression is recovered from the resolved
     * command's final argument and shown in the teaching form; anything
     * else displays as itself.
     */
    @NonNull
    public static String getDisplayCommandForResolved(
        @NonNull BabyAction action, @NonNull String resolvedCommand) {
        if (!isPythonCalcTemplate(action.commandTemplate)) {
            return resolvedCommand;
        }
        String expr = extractCalcExpression(resolvedCommand);
        return "python3 -c 'print(" + (expr != null ? expr : "式") + ")'";
    }

    @Nullable
    private static String extractCalcExpression(@NonNull String resolvedCommand) {
        // The template ends with ")' " followed by the shell-quoted input.
        int marker = resolvedCommand.lastIndexOf(")' ");
        if (marker < 0) return null;
        String quoted = resolvedCommand.substring(marker + 3).trim();
        if (quoted.length() >= 2 && quoted.startsWith("'") && quoted.endsWith("'")) {
            return quoted.substring(1, quoted.length() - 1).replace("'\\''", "'");
        }
        return quoted.isEmpty() ? null : quoted;
    }

    /** Flattens newlines and honestly truncates a command for one-line display. */
    @NonNull
    public static String truncateForDisplay(@NonNull String command) {
        String flat = command.replace('\n', ' ');
        if (flat.length() > 64) {
            return flat.substring(0, 64).trim() + "…";
        }
        return flat;
    }

    /**
     * Safely quotes an arbitrary string for POSIX /bin/sh using single quotes.
     * Single quotes inside the string are replaced with {@code '\''}.
     */
    @NonNull
    public static String shellQuote(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return "''";
        }
        return "'" + raw.replace("'", "'\\''") + "'";
    }

    /**
     * Builds a safe shell command from a template and user input by POSIX-quoting the argument.
     * Throws {@link IllegalArgumentException} if a required user input is blank.
     */
    @NonNull
    public static String buildSafeCommand(@NonNull String template, @Nullable String userArg) {
        boolean needsArg = template.contains(PLACEHOLDER_ARG) || template.contains(PLACEHOLDER_GLOB_ARG);
        if (!needsArg) {
            return template;
        }
        String trimmed = userArg != null ? userArg.trim() : "";
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("User argument must not be empty");
        }
        String result = template;
        if (result.contains(PLACEHOLDER_ARG)) {
            result = result.replace(PLACEHOLDER_ARG, shellQuote(trimmed));
        }
        if (result.contains(PLACEHOLDER_GLOB_ARG)) {
            result = result.replace(PLACEHOLDER_GLOB_ARG, shellQuote("*" + trimmed + "*"));
        }
        return result;
    }

    /**
     * Builds a safe {@code pkg install -y <packageName>} command, rejecting unsafe package names.
     */
    @NonNull
    public static String buildPackageInstallCommand(@NonNull String packageName) {
        String trimmed = packageName.trim();
        if (!trimmed.matches("^[a-zA-Z0-9][a-zA-Z0-9.+_-]{0,63}$")) {
            throw new IllegalArgumentException("Invalid package name: " + packageName);
        }
        return "pkg install -y " + trimmed;
    }

    /**
     * Guardrail check rejecting obviously destructive commands from
     * auto-running in Baby Mode. Token-based, so split spellings
     * ({@code rm -r -f}, {@code rm --recursive --force}) are caught just
     * like the combined {@code rm -rf}.
     */
    public static boolean isSafeForAutoRun(@Nullable String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }
        String lower = command.toLowerCase(Locale.US);
        if (lower.contains(":(){") || lower.contains("> /dev/")) {
            return false;
        }
        String[] tokens = lower.split("[\\s;|&]+");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.equals("rm")) {
                // Any recursive/force flag, in any spelling, blocks the run.
                for (int j = i + 1; j < tokens.length; j++) {
                    String arg = tokens[j];
                    if (arg.equals("--recursive") || arg.equals("--force")) {
                        return false;
                    }
                    if (arg.startsWith("-") && !arg.startsWith("--")
                        && (arg.indexOf('r') >= 0 || arg.indexOf('f') >= 0)) {
                        return false;
                    }
                }
            }
            if (token.equals("mkfs") || token.startsWith("mkfs.")) {
                return false;
            }
            if (token.equals("dd")) {
                return false;
            }
            if (token.equals("shutdown") || token.equals("reboot")
                || token.equals("poweroff")) {
                return false;
            }
            if (token.startsWith(">") && token.contains("/dev/")) {
                return false;
            }
        }
        return true;
    }
}
