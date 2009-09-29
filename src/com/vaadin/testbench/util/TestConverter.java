package com.vaadin.testbench.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;

import com.vaadin.testbench.util.SeleniumHTMLTestCaseParser.Command;

public class TestConverter {

    private static Map<String, String> knownBrowsers = new HashMap<String, String>();
    static {
        /*
         * Self mappings are to avoid unnecessary unknown browser warnings if
         * user wants to use selenium id strings.
         */
        knownBrowsers.put("firefox", "*chrome");
        knownBrowsers.put("*chrome", "*chrome");
        knownBrowsers.put("ie", "*iexplore");
        knownBrowsers.put("*iexplore", "*iexplore");
        knownBrowsers.put("opera", "*opera");
        knownBrowsers.put("*opera", "*opera");
        knownBrowsers.put("safari", "*safari");
        knownBrowsers.put("*safari", "*safari");
        knownBrowsers.put("googlechrome", "*googlechrome");
        knownBrowsers.put("*googlechrome", "*googlechrome");
    }
    private static final String JAVA_HEADER = "package {package};\n" + "\n"
            + "import com.vaadin.testbench.testcase.AbstractVaadinTestCase;\n"
            + "\n" + "public class {class} extends AbstractVaadinTestCase {\n"
            + "\n" + "public void setUp() throws Exception {\n"
            + "        setBrowser({browser});\n super.setUp();\n" + "}" + "\n";

    private static final String TEST_METHOD_HEADER = "public void test{testName}() throws Exception {\n";
    private static final String TEST_METHOD_FOOTER = "}\n";

    private static final String JAVA_FOOTER = "}\n";

    private static final String PACKAGE_DIR = "com/vaadin/automatedtests";

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: " + TestConverter.class.getName()
                    + " <output directory> <browsers> <html test files>");
            System.exit(1);
        }

        String outputDirectory = args[0];
        String browserString = args[1];
        String browsers[] = browserString.split(",");

        System.out.println("Using output directory: " + outputDirectory);
        createIfNotExists(outputDirectory);
        createIfNotExists(outputDirectory + File.separator + PACKAGE_DIR);

        for (String browser : browsers) {
            System.out.println("Generating tests for " + browser);

            OutputStream out = null;
            try {
                String browserId = knownBrowsers.get(browser.toLowerCase());
                if (browserId == null) {
                    System.err.println("Warning: Unknown browser: " + browser);
                }

                // Create a java file for holding all tests for this browser
                out = createJavaFileForBrowser(browser, outputDirectory);

                // Write the tests to the java file
                for (int i = 2; i < args.length; i++) {
                    String filename = args[i];
                    try {
                        String testMethod = createTestMethod(filename,
                                getTestName(filename));
                        out.write(testMethod.getBytes());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // Write the footer to the browser test class.
                writeJavaFooter(out);

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.flush();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void writeJavaFooter(OutputStream out) throws IOException {
        String footer = getJavaFooter();
        out.write(footer.getBytes());

    }

    private static String getTestName(String filename) {
        File f = new File(filename);
        return removeExtension(f.getName());
    }

    private static OutputStream createJavaFileForBrowser(String browser,
            String outputDirectory) throws IOException {
        File outputFile = getJavaFile(browser, outputDirectory);
        System.out.println("Creating " + outputFile + " for " + browser
                + " tests");
        FileOutputStream outputStream = new FileOutputStream(outputFile);

        outputStream.write(getJavaHeader(getSafeName(browser), browser));

        return outputStream;
    }

    private static String getSafeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private static void createIfNotExists(String directory) {
        File outputPath = new File(directory);
        if (!outputPath.exists()) {
            if (!outputPath.mkdirs()) {
                System.err.println("Could not create directory: " + directory);
                System.exit(1);
            } else {
                System.err.println("Created directory: " + directory);
            }
        }

    }

    private static String createTestMethod(String htmlFile, String testName)
            throws FileNotFoundException, IOException {

        String htmlSource = IOUtils.toString(new FileInputStream(htmlFile));
        htmlSource = htmlSource.replace("\"", "\\\"")
                .replaceAll("\\n", "\\\\n").replace("'", "\\'").replaceAll(
                        "\\r", "");

        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();

            List<Command> commands = parseTestCase(cx, scope, htmlSource);
            String testCaseMethod = createTestCaseMethod(testName, commands);
            return testCaseMethod;
            // System.out.println("Done");
        } finally {
            Context.exit();
        }

    }

    private static String createTestCaseMethod(String testName,
            List<Command> commands) {
        String testCaseHeader = getTestCaseHeader(testName);
        String testCaseBody = convertTestCaseToJava(commands, testName);
        String testCaseFooter = getTestCaseFooter(testName);
        // Add these inthe case a screenshot is wanted
        String windowFunctions = "doCommand(\"windowMaximize\", new String[] { \"\" });\n"
                + "doCommand(\"windowFocus\", new String[] { \"\" });\n";

        return testCaseHeader + windowFunctions + testCaseBody + testCaseFooter;
    }

    private static String removeExtension(String name) {
        return name.replaceAll("\\.[^\\.]*$", "");
    }

    private static String getTestCaseHeader(String testName) {
        String header = TEST_METHOD_HEADER;
        header = header.replace("{testName}", testName);

        return header;
    }

    private static String getTestCaseFooter(String testName) {
        // adding the softAssert so creating reference images throws a assert
        // failure at end of test
        String softAsserts = "if(!getSoftErrors().isEmpty()){\n"
                + "junit.framework.Assert.fail(\"Test was missing reference images.\");\n"
                + "}\n";
        String footer = TEST_METHOD_FOOTER;
        footer = footer.replace("{testName}", testName);

        return softAsserts + footer;
    }

    private static byte[] getJavaHeader(String className, String browser) {
        String header = JAVA_HEADER;
        header = header.replace("{class}", className);
        header = header.replace("{package}", getPackageName());

        String browserId = knownBrowsers.get(browser.toLowerCase());
        if (browserId == null) {
            browserId = browser;
        }

        header = header.replace("{browser}", "\"" + browserId + "\"");

        return header.getBytes();
    }

    private static String getJavaFooter() {
        return JAVA_FOOTER;
    }

    private static File getJavaFile(String browser, String outputDirectory) {
        String filenameSafeBrowser = getSafeName(browser);

        File file = new File(filenameSafeBrowser);
        String filename = removeExtension(file.getName());

        File outputFile = new File(outputDirectory + File.separator
                + getPackageDir() + File.separator + filename + ".java");

        return outputFile;
    }

    private static String convertTestCaseToJava(List<Command> commands,
            String testName) {
        StringBuilder javaSource = new StringBuilder();

        for (Command command : commands) {
            if (command.getCmd().equals("screenCapture")) {

                String identifier = "";
                boolean first = true;
                for (String param : command.getParams()) {
                    if (!first) {
                        identifier = param;
                    }
                    first = false;
                }
                javaSource.append("validateScreenshot(\"" + testName
                        + "\", 0.025, \"" + identifier + "\");\n");
            } else if (command.getCmd().equalsIgnoreCase("pause")) {
                String identifier = "";
                boolean first = true;
                for (String param : command.getParams()) {
                    if (first) {
                        identifier = param;
                    }
                    first = false;
                }
                javaSource.append("pause(" + identifier + ");\n");
            } else if (command.getCmd().equalsIgnoreCase("enterCharacter")) {
                String locator = "";
                String characters = "";
                boolean first = true;
                for (String param : command.getParams()) {
                    if (first) {
                        locator = param.replace("\\", "\\\\");
                    } else {
                        characters = param;
                    }

                    first = false;
                }
                javaSource.append("selenium.type(\"" + locator + "\", \""
                        + characters + "\");\n");
                if (characters.length() > 1) {
                    for (int i = 0; i < characters.length(); i++) {
                        javaSource.append("selenium.keyDown(\"" + locator
                                + "\", \"" + characters.charAt(i) + "\");\n");
                        javaSource.append("selenium.keyUp(\"" + locator
                                + "\", \"" + characters.charAt(i) + "\");\n");
                    }
                } else {
                    javaSource.append("selenium.keyDown(\"" + locator
                            + "\", \"" + characters + "\");\n");
                    javaSource.append("selenium.keyUp(\"" + locator + "\", \""
                            + characters + "\");\n");
                }
            } else if (command.getCmd().equalsIgnoreCase("pressSpecialKey")) {
                StringBuilder values = new StringBuilder();
                boolean first = true;
                for (String param : command.getParams()) {
                    if (first) {
                        values.append("\"" + param.replace("\\", "\\\\")
                                + "\", \"");
                    } else {
                        if (param.contains("\\")) {
                            values.append(param.replace("\\", "\\\\") + "\"");
                        } else if ("UP".equalsIgnoreCase(param)) {
                            values.append("\\\\38\"");
                        } else if ("DOWN".equalsIgnoreCase(param)) {
                            values.append("\\\\40\"");
                        } else if ("LEFT".equalsIgnoreCase(param)) {
                            values.append("\\\\37\"");
                        } else if ("RIGHT".equalsIgnoreCase(param)) {
                            values.append("\\\\39\"");
                        } else if ("ENTER".equalsIgnoreCase(param)) {
                            values.append("\\\\13\"");

                            // FIXME: Need an else or the generated test case
                            // won't compile

                            // } else if ("BACKSPACE".equalsIgnoreCase(param)) {
                            // values.append("\\\\8\"");
                        }
                    }

                    first = false;
                }
                javaSource.append("selenium.keyDown(" + values + ");\n");
                javaSource.append("selenium.keyPress(" + values + ");\n");
                javaSource.append("selenium.keyUp(" + values + ");\n");
            } else if (command.getCmd().equalsIgnoreCase("mouseClick")) {
                StringBuilder values = new StringBuilder();
                boolean first = true;
                String firstParam = "";
                for (String param : command.getParams()) {
                    if (first) {
                        firstParam = param;
                        values.append(param + "\", \"");
                    } else {
                        values.append(param);
                    }

                    first = false;
                }
                javaSource
                        .append("selenium.mouseDownAt(\"" + values + "\");\n");
                javaSource.append("selenium.mouseUpAt(\"" + values + "\");\n");
                javaSource.append("selenium.click(\"" + firstParam + "\");\n");
            } else if (command.getCmd().equalsIgnoreCase("verifyTextPresent")) {

                String identifier = "";
                boolean first = true;
                for (String param : command.getParams()) {
                    if (first) {
                        identifier = param;
                    }
                    first = false;
                }
                identifier = identifier.replace("\"", "\\\"").replaceAll("\\n",
                        "\\\\n");

                javaSource.append("assertTrue(\"Could not find: " + identifier
                        + "\", selenium.isTextPresent(\"" + identifier
                        + "\"));\n");
            } else {
                javaSource.append("doCommand(\"");
                javaSource.append(command.getCmd());
                javaSource.append("\",new String[] {");

                boolean first = true;
                for (String param : command.getParams()) {
                    if (!first) {
                        javaSource.append(",");
                    }
                    first = false;

                    javaSource.append("\"");
                    javaSource.append(param.replace("\"", "\\\"").replaceAll(
                            "\\n", "\\\\n"));
                    javaSource.append("\"");
                }
                javaSource.append("});\n");
            }
        }

        return javaSource.toString();
    }

    private static List<Command> parseTestCase(Context cx, Scriptable scope,
            String htmlSource) throws IOException {
        List<Command> commands = new ArrayList<Command>();

        cx.evaluateString(scope, "function load(a){}", "dummy-load", 1, null);
        cx
                .evaluateString(
                        scope,
                        "this.log = [];this.log.info = function log() {}; var log = this.log;",
                        "dummy-log", 1, null);

        loadScript("tools.js", scope, cx);
        loadScript("xhtml-entities.js", scope, cx);
        loadScript("html.js", scope, cx);
        loadScript("testCase.js", scope, cx);

        // htmlSource = htmlSource.replace("\\\n", "\\\\\n");
        cx.evaluateString(scope, "var src='" + htmlSource + "';",
                "htmlSourceDef", 1, null);
        cx.evaluateString(scope,
                "var testCase =  new TestCase();parse(testCase,src); ",
                "testCaseDef", 1, null);
        cx.evaluateString(scope, "var cmds = [];"
                + "var cmdList = testCase.commands;"
                + "for (var i=0; i < cmdList.length; i++) {"
                + "       var cmd = testCase.commands[i];"
                + "      if (cmd.type == 'command') {"
                + "              cmds.push(cmd);" + "      }" + "}" + "",
                "testCaseDef", 1, null);

        Object testCase = scope.get("cmds", scope);
        if (testCase instanceof NativeArray) {
            NativeArray arr = (NativeArray) testCase;
            for (int i = 0; i < arr.getLength(); i++) {
                NativeObject o = (NativeObject) arr.get(i, scope);
                Object target = o.get("target", scope);
                Object command = o.get("command", scope);
                Object value = o.get("value", scope);
                commands.add(new Command((String) command, (String) target,
                        (String) value));
            }
        }
        return commands;
    }

    private static void loadScript(String scriptName, Scriptable scope,
            Context cx) throws IOException {
        URL res = TestConverter.class.getResource(scriptName);
        cx.evaluateReader(scope, new InputStreamReader(res.openStream()),
                scriptName, 1, null);

    }

    private static String getPackageName() {
        return PACKAGE_DIR.replaceAll("/", ".");
    }

    private static String getPackageDir() {
        return PACKAGE_DIR.replace("/", File.separator);
    }
}