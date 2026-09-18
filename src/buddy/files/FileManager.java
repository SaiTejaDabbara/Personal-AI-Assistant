package buddy.files;

import buddy.resolver.WindowsLocationResolver;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

public class FileManager {

        private final WindowsLocationResolver locationResolver;

        public FileManager() {

                locationResolver = new WindowsLocationResolver();
        }

        // ========================================
        // OPEN COMMON FOLDERS
        // ========================================

        public String openCommonFolder(
                        String folderName) {

                File folder;

                String cleanName = folderName
                                .toLowerCase()
                                .trim();

                switch (cleanName) {

                        case "desktop":

                                folder = new File(
                                                locationResolver.getDesktopPath());

                                break;

                        case "downloads":
                        case "download":

                                folder = new File(
                                                System.getProperty(
                                                                "user.home"),
                                                "Downloads");

                                break;

                        case "documents":
                        case "document":

                                folder = new File(
                                                System.getProperty(
                                                                "user.home"),
                                                "Documents");

                                break;

                        case "pictures":
                        case "picture":

                                folder = new File(
                                                System.getProperty(
                                                                "user.home"),
                                                "Pictures");

                                break;

                        case "music":

                                folder = new File(
                                                System.getProperty(
                                                                "user.home"),
                                                "Music");

                                break;

                        case "videos":
                        case "video":

                                folder = new File(
                                                System.getProperty(
                                                                "user.home"),
                                                "Videos");

                                break;

                        default:

                                return null;
                }

                if (!folder.exists()) {

                        return "I could not find that folder.";
                }

                if (!folder.isDirectory()) {

                        return "That path is not a folder.";
                }

                return openFolder(
                                folder);
        }

        // ========================================
        // OPEN FOLDER USING PATH
        // ========================================

        public String openFolderPath(
                        String path) {

                if (path == null
                                || path.isBlank()) {

                        return "I could not find that folder.";
                }

                File folder = new File(
                                path);

                if (!folder.exists()) {

                        return "I could not find that folder.";
                }

                if (!folder.isDirectory()) {

                        return "That path is not a folder.";
                }

                return openFolder(
                                folder);
        }

        // ========================================
        // INTERNAL FOLDER OPEN
        // ========================================

        private String openFolder(
                        File folder) {

                try {

                        Desktop.getDesktop().open(
                                        folder);

                        return "Opening "
                                        + folder.getName()
                                        + " folder.";

                } catch (IOException e) {

                        return "Sorry, I could not open the folder.";
                }
        }

        // ========================================
        // OPEN FILE USING PATH
        // ========================================

        public String openFilePath(
                        String path) {

                if (path == null
                                || path.isBlank()) {

                        return "I could not find that file.";
                }

                File file = new File(
                                path);

                if (!file.exists()) {

                        return "I could not find that file.";
                }

                if (!file.isFile()) {

                        return "That path is not a file.";
                }

                try {

                        Desktop.getDesktop().open(
                                        file);

                        return "Opening "
                                        + file.getName()
                                        + ".";

                } catch (IOException e) {

                        return "Sorry, I could not open the file.";
                }
        }

        // ========================================
        // CREATE FOLDER
        // ========================================

        public String createFolder(
                        String folderName) {

                String userHome = System.getProperty(
                                "user.home");

                File folder = new File(
                                userHome,
                                folderName);

                return createFolderInternal(
                                folder);
        }

        // ========================================
        // CREATE FOLDER AT SPECIFIC LOCATION
        // ========================================

        public String createFolderAt(
                        String parentPath,
                        String folderName) {

                if (parentPath == null
                                || parentPath.isBlank()) {

                        return "The parent folder location is invalid.";
                }

                if (folderName == null
                                || folderName.isBlank()) {

                        return "The folder name is invalid.";
                }

                File parent = new File(
                                parentPath);

                if (!parent.exists()) {

                        return "I could not find the parent folder.";
                }

                if (!parent.isDirectory()) {

                        return "The parent path is not a folder.";
                }

                File folder = new File(
                                parent,
                                folderName);

                return createFolderInternal(
                                folder);
        }

        private String createFolderInternal(
                        File folder) {

                if (folder.exists()) {

                        return "That folder already exists.";
                }

                boolean created = folder.mkdirs();

                if (created) {

                        return "Folder "
                                        + folder.getName()
                                        + " created successfully.";
                }

                return "Sorry, I could not create the folder.";
        }

        // ========================================
        // CREATE FILE
        // ========================================

        public String createFile(
                        String fileName) {

                String userHome = System.getProperty(
                                "user.home");

                File file = new File(
                                userHome,
                                fileName);

                return createFileInternal(
                                file);
        }

        // ========================================
        // CREATE FILE AT SPECIFIC LOCATION
        // ========================================

        public String createFileAt(
                        String parentPath,
                        String fileName) {

                if (parentPath == null
                                || parentPath.isBlank()) {

                        return "The parent folder location is invalid.";
                }

                if (fileName == null
                                || fileName.isBlank()) {

                        return "The file name is invalid.";
                }

                File parent = new File(
                                parentPath);

                if (!parent.exists()) {

                        return "I could not find the parent folder.";
                }

                if (!parent.isDirectory()) {

                        return "The parent path is not a folder.";
                }

                File file = new File(
                                parent,
                                fileName);

                return createFileInternal(
                                file);
        }

        private String createFileInternal(
                        File file) {

                try {

                        if (file.exists()) {

                                return "That file already exists.";
                        }

                        boolean created = file.createNewFile();

                        if (created) {

                                return "File "
                                                + file.getName()
                                                + " created successfully.";
                        }

                        return "Sorry, I could not create the file.";

                } catch (IOException e) {

                        return "Sorry, I could not create the file.";
                }
        }

        // ========================================
        // GET REAL WINDOWS DESKTOP
        // ========================================

        public String getDesktopPath() {

                return locationResolver.getDesktopPath();
        }
}