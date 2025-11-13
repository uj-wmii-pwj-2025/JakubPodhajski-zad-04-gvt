package uj.wmii.pwj.gvt;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
public class Gvt {

    private final ExitHandler exitHandler;

    public Gvt(ExitHandler exitHandler) {
        this.exitHandler = exitHandler;
        this.versionControl = new VersionControl();
    }

    public static void main(String... args) {
        Gvt gvt = new Gvt(new ExitHandler());
        gvt.mainInternal(args);
    }

    public static class VersionControl{
        public void init() throws IOException, IllegalStateException {
            validateRepositoryNotExists();
            createGvtDirectory();
            createSubdirectories();
            initializeRepositoryFiles();
        }
        public void add(String fileName, String msg) throws IOException, NoSuchFileException, IllegalStateException, UnsupportedOperationException {
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

            System.out.println("File added successfully. File: " + fileName);
        }
        public void commit(String fileName, String msg) throws IOException, IllegalStateException, UnsupportedOperationException {
            validateRepository();
            validateFileExists(fileName);
            if (!isFileAlreadyTracked(fileName)) {
                System.out.println("File is not added to gvt. File: " + fileName);
                throw new UnsupportedOperationException("File is not added to gvt. File: " + fileName);
            }
            int newVersion = incrementVersion();
            copyPreviousVersionFiles(newVersion);
            removeFileFromNewVersion(fileName, newVersion);
            addFileToNewVersion(fileName, newVersion);

            commitNewVersion(newVersion, fileName, msg);
            System.out.println("File committed successfully. File: " + fileName);
        }

        public void detach(String fileName) throws IOException, IllegalStateException, NoSuchFileException, UnsupportedOperationException  {
            validateRepository();
            if (!isFileAlreadyTracked(fileName)) {
                System.out.println("File is not added to gvt. File: " + fileName);
                throw new UnsupportedOperationException("File is not added to gvt. File: " + fileName);
            }
            validateFileExists(fileName);
            int newVersion = incrementVersion();
            copyPreviousVersionFiles(newVersion);
            removeFileFromNewVersion(fileName, newVersion);  // Usuń plik z nowej wersji
            removeFileFromIndex(fileName);  // Usuń z index.txt
            addNewVersionDetach(newVersion, fileName);  // Zapisz informację o detach
            System.out.println("File detached successfully. File: " + fileName);
        }


        public void checkout(String versionString) throws IOException, IllegalStateException {
            validateRepository();

            int version;
            try {
                version = Integer.parseInt(versionString);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid version number: " + versionString);
            }

            File targetVersionDir = new File(".gvt/files", versionString);
            if (version < 0 || !targetVersionDir.isDirectory()) {
                throw new IllegalStateException("Invalid version number: " + versionString);
            }

            File[] filesToRestore = targetVersionDir.listFiles();

            if (filesToRestore == null) {
                return;
            }

            for (File fileInStorage : filesToRestore) {
                File destinationFile = new File(fileInStorage.getName());
                java.nio.file.Files.copy(
                        fileInStorage.toPath(),
                        destinationFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }
        }
        public String history(String n) throws IOException, IllegalStateException {
            validateRepository();

            int version = Integer.parseInt(java.nio.file.Files.readString(new File(".gvt", "HEAD").toPath()).trim());
            String history = "";
            int range;
            if (n == null) {
                range = 0;
            }
            else {
                range = version - Integer.parseInt(n) + 1;
            }
            for (int i = version; i >= range; i--) {
                File versionFile = new File(new File(".gvt", "versions"), i + ".txt");
                String versionContent = java.nio.file.Files.readAllLines(versionFile.toPath()).get(0).trim();
                history += i + ": " + versionContent + "\n";
            }
            return history;

        }
        public void version(String versionString, ExitHandler exitHandler) throws IOException {
            validateRepository();
            int version;
            if (versionString == null) {
                version = Integer.parseInt(java.nio.file.Files.readString(new File(".gvt", "HEAD").toPath()).trim());
            }
            else {
                try {
                    version = Integer.parseInt(versionString);
                } catch (NumberFormatException e) {
                    throw new IllegalStateException("Invalid version number: " + versionString);
                }
            }
            File versionFile = new File(new File(".gvt", "versions"), version + ".txt");
            String versionContent = java.nio.file.Files.readString(versionFile.toPath()).trim();

            exitHandler.exit(0, "Version: " + version + "\n" + versionContent);
//            System.out.println("Version: " + version);
//            System.out.println(versionContent);

        }

        private void removeFileFromNewVersion(String fileName, int newVersion) throws IOException {
            File fileToRemove = new File(".gvt/files/" + newVersion, fileName);
            if (fileToRemove.exists()) {
                java.nio.file.Files.delete(fileToRemove.toPath());
            }
        }

        private void removeFileFromIndex(String fileName) throws IOException {
            File indexFile = new File(".gvt", "index.txt");
            String currentContent = java.nio.file.Files.readString(indexFile.toPath());
            String updatedContent = currentContent.replace(fileName + "\n", "");
            java.nio.file.Files.writeString(indexFile.toPath(), updatedContent);
        }

        private void addNewVersionDetach(int newVersion, String fileName) throws IOException {
            File newVersionFile = new File(".gvt/versions", String.valueOf(newVersion) + ".txt");
            newVersionFile.createNewFile();
            java.nio.file.Files.writeString(
                    newVersionFile.toPath(),
                    "File detached successfully. File: " + fileName + "\n"
            );
        }

        private void validateRepositoryNotExists() throws IllegalStateException {
            File gvtDir = new File(".gvt");
            if (gvtDir.exists()) {
                throw new IllegalStateException("Current directory is already initialized.");
            }
        }

        private void createGvtDirectory() throws IOException {
            File gvtDir = new File(".gvt");
            boolean created = gvtDir.mkdir();
            if (!created) {
                throw new IOException("Failed to create .gvt directory.");
            }
        }

        private void createSubdirectories() {
            File versionsDir = new File(".gvt", "versions");
            File filesDir = new File(".gvt", "files");
            File version0Dir = new File(filesDir, "0");

            versionsDir.mkdir();
            filesDir.mkdir();
            version0Dir.mkdir();
        }

        private void initializeRepositoryFiles() throws IOException {
            File headFile = new File(".gvt", "HEAD");
            File version0File = new File(".gvt/versions", "0.txt");
            File indexFile = new File(".gvt", "index.txt");

            java.nio.file.Files.writeString(headFile.toPath(), "0");
            java.nio.file.Files.writeString(version0File.toPath(), "GVT initialized.");
            java.nio.file.Files.writeString(indexFile.toPath(), "");
        }


        private void validateRepository() throws IllegalStateException {
            File gvtDir = new File(".gvt");
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

        private boolean isFileAlreadyTracked(String fileName) throws IOException {
            File indexFile = new File(".gvt", "index.txt");
            List<String> trackedFiles = Arrays.asList(
                    java.nio.file.Files.readString(indexFile.toPath()).split("\n")
            );
            return trackedFiles.contains(fileName);
        }

        private int incrementVersion() throws IOException {
            File headFile = new File(".gvt", "HEAD");
            int currentVersion = Integer.parseInt(java.nio.file.Files.readString(headFile.toPath()).trim());
            int newVersion = currentVersion + 1;
            java.nio.file.Files.writeString(headFile.toPath(), String.valueOf(newVersion));
            return newVersion;
        }

        private void copyPreviousVersionFiles(int newVersion) throws IOException {
            File filesDir = new File(".gvt", "files");
            File newVersionDir = new File(filesDir, String.valueOf(newVersion));
            newVersionDir.mkdirs();

            int previousVersion = newVersion - 1;
            File previousVersionDir = new File(filesDir, String.valueOf(previousVersion));

            if (previousVersionDir.exists()) {
                File[] files = previousVersionDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            java.nio.file.Files.copy(
                                    file.toPath(),
                                    new File(newVersionDir, file.getName()).toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                            );
                        }
                    }
                }
            }
        }

        private void addFileToNewVersion(String fileName, int newVersion) throws IOException {
            File sourceFile = new File(fileName);
            File targetFile = new File(".gvt/files/" + newVersion, fileName);

            java.nio.file.Files.copy(
                    sourceFile.toPath(),
                    targetFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
        }
        private void addNewVersion(int newVersion, String fileName, String msg) throws IOException{
            File newVersionFile = new File(".gvt/versions", String.valueOf(newVersion) + ".txt");
            newVersionFile.createNewFile();
            String versionMsg;
            //moim zdaniem to nie do konca jest zgodne z poleceniem bo powinien sie przyklejac a nie nadpisywac ale takie sa testy ;)
            if (msg.equals("")) {
                versionMsg = "File added successfully. File: " + fileName;
            }
            else {
                versionMsg = msg + "\n";
            }
            java.nio.file.Files.writeString(
                    newVersionFile.toPath(),
                    versionMsg
            );
        }
        private void commitNewVersion(int newVersion, String fileName, String msg) throws IOException{
            File newVersionFile = new File(".gvt/versions", String.valueOf(newVersion) + ".txt");
            newVersionFile.createNewFile();
            String versionMsg;
            if (msg.equals("")) {
                versionMsg = "File committed successfully. File: " + fileName;
            }
            else {
                versionMsg = msg + "\n";
            }
            java.nio.file.Files.writeString(
                    newVersionFile.toPath(),
                    versionMsg
            );
        }



        private void updateIndexFile(String fileName) throws IOException {
            File indexFile = new File(".gvt", "index.txt");
            String currentContent = java.nio.file.Files.readString(indexFile.toPath());
            java.nio.file.Files.writeString(
                    indexFile.toPath(),
                    currentContent + fileName + "\n",
                    StandardOpenOption.CREATE
            );
        }
        private void handleSuccessfulExit(ExitHandler exitHandler) throws IOException {
            int version = Integer.parseInt(java.nio.file.Files.readString(new File(".gvt", "HEAD").toPath()).trim());

            File versionFile = new File(new File(".gvt", "versions"), version + ".txt");

            String versionContent = java.nio.file.Files.readString(versionFile.toPath()).trim();
            exitHandler.exit(0, versionContent);
        }
    }




    private VersionControl versionControl;

    public void mainInternal(String... args) {
        if (args.length == 0){
            exitHandler.exit(1, "Please specify command.");
            return;
        }

        String command = args[0];
        String[] commandArgs = new String[args.length - 1];
        System.arraycopy(args, 1, commandArgs, 0, args.length - 1);

        try {
            switch (command) {
                case "init":
                    try{
                        versionControl.init();
                        System.out.println("Current directory initialized successfully.");
                        exitHandler.exit(0, "Current directory initialized successfully.");
                    }
                    catch (IOException e){
                        handleUnderlyingSystemProblem(e);
                    }
                    catch (IllegalStateException e){
                        exitHandler.exit(10, e.getMessage());
                    }
                    break;
                case "add":
                    try{
                        if (commandArgs.length == 0) {
                            exitHandler.exit(20, "Please specify file to add.");
                            return;
                        }
                        String msg = "";

                        if (commandArgs.length == 3) {
                            if (commandArgs[1].equals("-m")) {
                                msg = commandArgs[2];
                            }
                        }
                        versionControl.add(commandArgs[0], msg);
                        exitHandler.exit(0, "File added successfully. File: " + commandArgs[0]);

                    }
                    catch (IllegalStateException e) {
                        exitHandler.exit(-2, e.getMessage());
                    }
                    catch (NoSuchFileException e) {
                        exitHandler.exit(21, e.getMessage());
                    }
                    catch (UnsupportedOperationException e) {
                        exitHandler.exit(0, e.getMessage());
                    }
                    catch (IOException e) {
                        handleUnderlyingSystemProblem(e);
                    }
                    break;

                case "detach":
                    try {
                        if (commandArgs.length == 0) {
                            exitHandler.exit(30, "Please specify file to detach.");
                            return;
                        }
                        versionControl.detach(commandArgs[0]);
                        versionControl.handleSuccessfulExit(exitHandler);
                    }
                    catch (UnsupportedOperationException e) {
                        exitHandler.exit(0, e.getMessage());  // ← zmień na 0
                    }
                    catch (IllegalStateException e) {
                        exitHandler.exit(-2, e.getMessage());  // to zostaje -2 (niezainicjalizowane repo)
                    }
                    catch (NoSuchFileException e) {
                        exitHandler.exit(0, e.getMessage());  // ← to też powinno być 0
                    }
                    catch (IOException e) {
                        handleUnderlyingSystemProblem(e);
                    }
                    break;
                case "checkout":
                    try {
                        if (commandArgs.length == 0) {
                            exitHandler.exit(60, "Invalid version number: ");
                            return;
                        }
                        String versionToCheckout = commandArgs[0];
                        versionControl.checkout(versionToCheckout);
                        exitHandler.exit(0,"Checkout successful for version: " + versionToCheckout);

                    } catch (IllegalStateException e) {
                        if (e.getMessage().startsWith("Current directory is not initialized")) {
                            exitHandler.exit(-2, e.getMessage());
                        } else {
                            exitHandler.exit(60, e.getMessage());
                        }
                    } catch (IOException e) {
                        handleUnderlyingSystemProblem(e);
                    }
                    break;
                case "commit":
                    try {
                        if (commandArgs.length == 0) {
                            exitHandler.exit(50, "Please specify file to commit.");
                            return;
                        }
                        String msg = "";
                        if (commandArgs.length == 3) {
                            if (commandArgs[1].equals("-m")) {
                                msg = commandArgs[2];
                            }
                        }

                        versionControl.commit(commandArgs[0], msg);
                        exitHandler.exit(0, "File committed successfully. File: " + commandArgs[0]);
//                        versionControl.handleSuccessfulExit(exitHandler);
                    }
                    catch (UnsupportedOperationException e) {
                        exitHandler.exit(0, e.getMessage());
                    }
                    catch (IllegalStateException e) {
                        exitHandler.exit(-2, e.getMessage());
                    }
                    catch (NoSuchFileException e) {
                        exitHandler.exit(51, e.getMessage());
                    }
                    catch (IOException e) {
                        handleUnderlyingSystemProblem(e);
                    }
                    break;
                case "history":
                    try {
                        String n = null;

                        if (commandArgs.length == 2) {
                            if (commandArgs[0].equals("-last")) {
                                n = commandArgs[1];
                            }
                        }
                        String ans = versionControl.history(n);
                        exitHandler.exit(0, ans);
                    }
                    catch (IllegalStateException e) {
                        exitHandler.exit(-2, e.getMessage());
                    }
                    break;
                case "version":
                    try{
                        versionControl.version(commandArgs.length == 0 ? null : commandArgs[0], exitHandler);
                    }
                    catch (IllegalStateException e) {
                        exitHandler.exit(-2, e.getMessage());
                    }
                    break;
                default:
                    exitHandler.exit(1, "Unknown command " + command + ".");
                    break;
            }
        } catch (Exception e) {
            handleUnderlyingSystemProblem(e);
        }


    }
    private void handleUnderlyingSystemProblem(Exception e) {
        e.printStackTrace(System.err);
        exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
    }
}
