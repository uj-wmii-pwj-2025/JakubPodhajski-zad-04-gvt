package uj.wmii.pwj.gvt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

public class Gvt {
    private static final String GVT_DIR = ".gvt";
    private static final String FILES_DIR = ".gvt/files";
    private static final String VERSIONS_DIR = ".gvt/versions";
    private static final String HEAD_FILE = ".gvt/HEAD";
    private static final String INDEX_FILE = ".gvt/index.txt";

    // Exit codes
    private static final int ERROR_COMMAND_HANDLING = 1;
    private static final int ERROR_ALREADY_INITIALIZED = 10;
    private static final int ERROR_ADD_NO_FILE = 20;
    private static final int ERROR_ADD_FILE_NOT_FOUND = 21;
    private static final int ERROR_DETACH_NO_FILE = 30;
    private static final int ERROR_COMMIT_NO_FILE = 50;
    private static final int ERROR_COMMIT_FILE_NOT_FOUND = 51;
    private static final int ERROR_INVALID_VERSION = 60;
    private static final int ERROR_NOT_INITIALIZED = -2;
    private static final int ERROR_SYSTEM_PROBLEM = -3;

    private final ExitHandler exitHandler;
    private final VersionControl versionControl;

    public Gvt(ExitHandler exitHandler) {
        this.exitHandler = exitHandler;
        this.versionControl = new VersionControl();
    }

    public static void main(String... args) {
        Gvt gvt = new Gvt(new ExitHandler());
        gvt.mainInternal(args);
    }

    public void mainInternal(String... args) {
        if (args.length == 0) {
            exitHandler.exit(ERROR_COMMAND_HANDLING, "Please specify command.");
            return;
        }

        String command = args[0];
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);

        try {
            switch (command) {
                case "init":
                    handleInit();
                    break;
                case "add":
                    handleAdd(commandArgs);
                    break;
                case "detach":
                    handleDetach(commandArgs);
                    break;
                case "checkout":
                    handleCheckout(commandArgs);
                    break;
                case "commit":
                    handleCommit(commandArgs);
                    break;
                case "history":
                    handleHistory(commandArgs);
                    break;
                case "version":
                    handleVersion(commandArgs);
                    break;
                default:
                    exitHandler.exit(ERROR_COMMAND_HANDLING, "Unknown command " + command + ".");
                    break;
            }
        } catch (Exception e) {
            handleUnderlyingSystemProblem(e);
        }
    }

    private void handleInit() {
        try {
            versionControl.init();
            exitHandler.exit(0, "Current directory initialized successfully.");
        } catch (IOException e) {
            handleUnderlyingSystemProblem(e);
        } catch (IllegalStateException e) {
            exitHandler.exit(ERROR_ALREADY_INITIALIZED, e.getMessage());
        }
    }

    private void handleAdd(String[] commandArgs) {
        try {
            if (commandArgs.length == 0) {
                exitHandler.exit(ERROR_ADD_NO_FILE, "Please specify file to add.");
                return;
            }
            String msg = extractMessage(commandArgs);
            versionControl.add(commandArgs[0], msg);
            exitHandler.exit(0, "File added successfully. File: " + commandArgs[0]);
        } catch (IllegalStateException e) {
            exitHandler.exit(ERROR_NOT_INITIALIZED, e.getMessage());
        } catch (NoSuchFileException e) {
            exitHandler.exit(ERROR_ADD_FILE_NOT_FOUND, e.getMessage());
        } catch (UnsupportedOperationException e) {
            exitHandler.exit(0, e.getMessage());
        } catch (IOException e) {
            handleUnderlyingSystemProblem(e);
        }
    }

    private void handleDetach(String[] commandArgs) {
        try {
            if (commandArgs.length == 0) {
                exitHandler.exit(ERROR_DETACH_NO_FILE, "Please specify file to detach.");
                return;
            }
            versionControl.detach(commandArgs[0]);
            versionControl.handleSuccessfulExit(exitHandler);
        } catch (UnsupportedOperationException e) {
            exitHandler.exit(0, e.getMessage());
        } catch (IllegalStateException e) {
            exitHandler.exit(ERROR_NOT_INITIALIZED, e.getMessage());
        } catch (NoSuchFileException e) {
            exitHandler.exit(0, e.getMessage());
        } catch (IOException e) {
            handleUnderlyingSystemProblem(e);
        }
    }

    private void handleCheckout(String[] commandArgs) {
        try {
            if (commandArgs.length == 0) {
                exitHandler.exit(ERROR_INVALID_VERSION, "Invalid version number: ");
                return;
            }
            String versionToCheckout = commandArgs[0];
            versionControl.checkout(versionToCheckout);
            exitHandler.exit(0, "Checkout successful for version: " + versionToCheckout);
        } catch (IllegalStateException e) {
            if (e.getMessage().startsWith("Current directory is not initialized")) {
                exitHandler.exit(ERROR_NOT_INITIALIZED, e.getMessage());
            } else {
                exitHandler.exit(ERROR_INVALID_VERSION, e.getMessage());
            }
        } catch (IOException e) {
            handleUnderlyingSystemProblem(e);
        }
    }

    private void handleCommit(String[] commandArgs) {
        try {
            if (commandArgs.length == 0) {
                exitHandler.exit(ERROR_COMMIT_NO_FILE, "Please specify file to commit.");
                return;
            }
            String msg = extractMessage(commandArgs);
            versionControl.commit(commandArgs[0], msg);
            exitHandler.exit(0, "File committed successfully. File: " + commandArgs[0]);
        } catch (UnsupportedOperationException e) {
            exitHandler.exit(0, e.getMessage());
        } catch (IllegalStateException e) {
            exitHandler.exit(ERROR_NOT_INITIALIZED, e.getMessage());
        } catch (NoSuchFileException e) {
            exitHandler.exit(ERROR_COMMIT_FILE_NOT_FOUND, e.getMessage());
        } catch (IOException e) {
            handleUnderlyingSystemProblem(e);
        }
    }

    private void handleHistory(String[] commandArgs) {
        try {
            String n = null;
            if (commandArgs.length == 2 && commandArgs[0].equals("-last")) {
                n = commandArgs[1];
            }
            String ans = versionControl.history(n);
            exitHandler.exit(0, ans);
        } catch (IllegalStateException e) {
            exitHandler.exit(ERROR_NOT_INITIALIZED, e.getMessage());
        } catch (IOException e) {
            handleUnderlyingSystemProblem(e);
        }
    }

    private void handleVersion(String[] commandArgs) {
        try {
            String versionArg = commandArgs.length == 0 ? null : commandArgs[0];
            versionControl.version(versionArg, exitHandler);
        } catch (IllegalStateException e) {
            exitHandler.exit(ERROR_NOT_INITIALIZED, e.getMessage());
        } catch (IOException e) {
            handleUnderlyingSystemProblem(e);
        }
    }

    private String extractMessage(String[] commandArgs) {
        if (commandArgs.length == 3 && commandArgs[1].equals("-m")) {
            return commandArgs[2];
        }
        return "";
    }

    private void handleUnderlyingSystemProblem(Exception e) {
        e.printStackTrace(System.err);
        exitHandler.exit(ERROR_SYSTEM_PROBLEM, "Underlying system problem. See ERR for details.");
    }

    public static class VersionControl {

        public void init() throws IOException, IllegalStateException {
            validateRepositoryNotExists();
            createGvtDirectory();
            createSubdirectories();
            initializeRepositoryFiles();
        }

        public void add(String fileName, String msg) throws IOException, NoSuchFileException,
                IllegalStateException, UnsupportedOperationException {
            validateRepository();

            if (isFileAlreadyTracked(fileName)) {
                throw new UnsupportedOperationException("File already added. File: " + fileName);
            }
            validateFileExists(fileName);

            int newVersion = incrementVersion();
            copyPreviousVersionFiles(newVersion);
            addFileToNewVersion(fileName, newVersion);
            updateIndexFile(fileName);
            addNewVersion(newVersion, fileName, msg);

        }

        public void commit(String fileName, String msg) throws IOException, IllegalStateException,
                UnsupportedOperationException {
            validateRepository();
            validateFileExists(fileName);

            if (!isFileAlreadyTracked(fileName)) {
                throw new UnsupportedOperationException("File is not added to gvt. File: " + fileName);
            }

            int newVersion = incrementVersion();
            copyPreviousVersionFiles(newVersion);
            removeFileFromNewVersion(fileName, newVersion);
            addFileToNewVersion(fileName, newVersion);
            commitNewVersion(newVersion, fileName, msg);

        }

        public void detach(String fileName) throws IOException, IllegalStateException,
                NoSuchFileException, UnsupportedOperationException {
            validateRepository();

            if (!isFileAlreadyTracked(fileName)) {
                throw new UnsupportedOperationException("File is not added to gvt. File: " + fileName);
            }
            validateFileExists(fileName);

            int newVersion = incrementVersion();
            copyPreviousVersionFiles(newVersion);
            removeFileFromNewVersion(fileName, newVersion);
            removeFileFromIndex(fileName);
            addNewVersionDetach(newVersion, fileName);

        }

        public void checkout(String versionString) throws IOException, IllegalStateException {
            validateRepository();

            int version = parseVersion(versionString);
            File targetVersionDir = new File(FILES_DIR, versionString);

            if (version < 0 || !targetVersionDir.isDirectory()) {
                throw new IllegalStateException("Invalid version number: " + versionString);
            }

            File[] filesToRestore = targetVersionDir.listFiles();
            if (filesToRestore == null) {
                return;
            }

            for (File fileInStorage : filesToRestore) {
                File destinationFile = new File(fileInStorage.getName());
                Files.copy(fileInStorage.toPath(), destinationFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }

        public String history(String n) throws IOException, IllegalStateException {
            validateRepository();

            int currentVersion = getCurrentVersion();
            int startVersion = calculateStartVersion(currentVersion, n);

            StringBuilder history = new StringBuilder();
            for (int i = currentVersion; i >= startVersion; i--) {
                File versionFile = new File(VERSIONS_DIR, i + ".txt");
                String versionContent = Files.readAllLines(versionFile.toPath()).get(0).trim();
                history.append(i).append(": ").append(versionContent).append("\n");
            }
            return history.toString();
        }

        public void version(String versionString, ExitHandler exitHandler) throws IOException {
            validateRepository();

            int version = (versionString == null) ? getCurrentVersion() : parseVersion(versionString);
            File versionFile = new File(VERSIONS_DIR, version + ".txt");
            String versionContent = Files.readString(versionFile.toPath()).trim();

            exitHandler.exit(0, "Version: " + version + "\n" + versionContent);
        }

        private void validateRepositoryNotExists() throws IllegalStateException {
            File gvtDir = new File(GVT_DIR);
            if (gvtDir.exists()) {
                throw new IllegalStateException("Current directory is already initialized.");
            }
        }

        private void validateRepository() throws IllegalStateException {
            File gvtDir = new File(GVT_DIR);
            if (!gvtDir.exists()) {
                throw new IllegalStateException("Current directory is not initialized. Please use init command to initialize.");
            }
        }

        private void validateFileExists(String fileName) throws NoSuchFileException {
            File file = new File(fileName);
            if (!file.exists()) {
                throw new NoSuchFileException("File not found. File: " + fileName);
            }
        }

        private void createGvtDirectory() throws IOException {
            File gvtDir = new File(GVT_DIR);
            if (!gvtDir.mkdir()) {
                throw new IOException("Failed to create .gvt directory.");
            }
        }

        private void createSubdirectories() {
            new File(VERSIONS_DIR).mkdir();
            new File(FILES_DIR).mkdir();
            new File(FILES_DIR, "0").mkdir();
        }

        private void initializeRepositoryFiles() throws IOException {
            Files.writeString(new File(HEAD_FILE).toPath(), "0");
            Files.writeString(new File(VERSIONS_DIR, "0.txt").toPath(), "GVT initialized.");
            Files.writeString(new File(INDEX_FILE).toPath(), "");
        }

        private boolean isFileAlreadyTracked(String fileName) throws IOException {
            File indexFile = new File(INDEX_FILE);
            List<String> trackedFiles = Arrays.asList(Files.readString(indexFile.toPath()).split("\n"));
            return trackedFiles.contains(fileName);
        }

        private int getCurrentVersion() throws IOException {
            return Integer.parseInt(Files.readString(new File(HEAD_FILE).toPath()).trim());
        }

        private int incrementVersion() throws IOException {
            int newVersion = getCurrentVersion() + 1;
            Files.writeString(new File(HEAD_FILE).toPath(), String.valueOf(newVersion));
            return newVersion;
        }

        private int parseVersion(String versionString) {
            try {
                return Integer.parseInt(versionString);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid version number: " + versionString);
            }
        }

        private int calculateStartVersion(int currentVersion, String n) {
            return (n == null) ? 0 : currentVersion - Integer.parseInt(n) + 1;
        }

        private void copyPreviousVersionFiles(int newVersion) throws IOException {
            File newVersionDir = new File(FILES_DIR, String.valueOf(newVersion));
            newVersionDir.mkdirs();

            File previousVersionDir = new File(FILES_DIR, String.valueOf(newVersion - 1));
            if (!previousVersionDir.exists()) {
                return;
            }

            File[] files = previousVersionDir.listFiles();
            if (files == null) {
                return;
            }

            for (File file : files) {
                if (file.isFile()) {
                    Files.copy(file.toPath(),
                            new File(newVersionDir, file.getName()).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        private void addFileToNewVersion(String fileName, int newVersion) throws IOException {
            File sourceFile = new File(fileName);
            File targetFile = new File(new File(FILES_DIR, String.valueOf(newVersion)), fileName);
            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        private void removeFileFromNewVersion(String fileName, int newVersion) throws IOException {
            File fileToRemove = new File(new File(FILES_DIR, String.valueOf(newVersion)), fileName);
            if (fileToRemove.exists()) {
                Files.delete(fileToRemove.toPath());
            }
        }

        private void updateIndexFile(String fileName) throws IOException {
            File indexFile = new File(INDEX_FILE);
            String currentContent = Files.readString(indexFile.toPath());
            Files.writeString(indexFile.toPath(), currentContent + fileName + "\n",
                    StandardOpenOption.CREATE);
        }

        private void removeFileFromIndex(String fileName) throws IOException {
            File indexFile = new File(INDEX_FILE);
            String currentContent = Files.readString(indexFile.toPath());
            String updatedContent = currentContent.replace(fileName + "\n", "");
            Files.writeString(indexFile.toPath(), updatedContent);
        }

        private void addNewVersion(int newVersion, String fileName, String msg) throws IOException {
            File newVersionFile = new File(VERSIONS_DIR, newVersion + ".txt");
            newVersionFile.createNewFile();

            String versionMsg = msg.isEmpty()
                    ? "File added successfully. File: " + fileName
                    : msg + "\n";

            Files.writeString(newVersionFile.toPath(), versionMsg);
        }

        private void commitNewVersion(int newVersion, String fileName, String msg) throws IOException {
            File newVersionFile = new File(VERSIONS_DIR, newVersion + ".txt");
            newVersionFile.createNewFile();

            String versionMsg = msg.isEmpty()
                    ? "File committed successfully. File: " + fileName
                    : msg + "\n";

            Files.writeString(newVersionFile.toPath(), versionMsg);
        }

        private void addNewVersionDetach(int newVersion, String fileName) throws IOException {
            File newVersionFile = new File(VERSIONS_DIR, newVersion + ".txt");
            newVersionFile.createNewFile();
            Files.writeString(newVersionFile.toPath(),
                    "File detached successfully. File: " + fileName + "\n");
        }

        private void handleSuccessfulExit(ExitHandler exitHandler) throws IOException {
            int version = getCurrentVersion();
            File versionFile = new File(VERSIONS_DIR, version + ".txt");
            String versionContent = Files.readString(versionFile.toPath()).trim();
            exitHandler.exit(0, versionContent);
        }
    }
}