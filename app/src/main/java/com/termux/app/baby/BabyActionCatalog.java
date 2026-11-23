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
                BabyAction.ActionType.TRY_PYTHON,
                BabyAction.Category.LEARN,
                PYTHON_HELLO_COMMAND,
                BabyAction.OutputFormat.PYTHON,
                0,
                0,
                BabyCommandExecutor.DEFAULT_TIMEOUT_SECONDS
            )
        ));
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
        recipes.add(new BabyAction(
            "recipe_disk",
            R.string.baby_recipe_disk_title,
            R.string.baby_recipe_disk_sub,
            R.drawable.ic_baby_device,
            BabyAction.ActionType.DIRECT_COMMAND,
            BabyAction.Category.TOOLS,
            "uname -m && df -h \"$HOME\"",
            BabyAction.OutputFormat.DEVICE_INFO,
            0,
            0,
            BabyCommandExecutor.DEFAULT_TIMEOUT_SECONDS
        ));
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
     * Returns a short, readable command preview for display in Command Helper cards.
     */
    @NonNull
    public static String getDisplayCommandPreview(@NonNull BabyAction action) {
        if ("recipe_python_calc".equals(action.id)) {
            return "python3 -c 'print(...)'";
        }
        if (action.commandTemplate == null) {
            return "";
        }
        return action.commandTemplate
            .replace(PLACEHOLDER_ARG, "<...>")
            .replace(PLACEHOLDER_GLOB_ARG, "'*...*'");
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
     * Guardrail check rejecting obviously destructive commands from auto-running in Baby Mode.
     */
    public static boolean isSafeForAutoRun(@Nullable String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }
        String lower = command.toLowerCase(Locale.US);
        return !lower.contains("rm -rf")
            && !lower.contains("rm -fr")
            && !lower.contains("mkfs")
            && !lower.contains("dd if=")
            && !lower.contains(":(){");
    }
}
