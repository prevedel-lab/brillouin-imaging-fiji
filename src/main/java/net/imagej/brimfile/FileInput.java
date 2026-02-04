package net.imagej.brimfile;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;

import java.io.File;

/**
 * Helper utilities for selecting and validating BRIM file inputs.
 */
final class FileInput {

    private FileInput() {
        // Utility class
    }

    /**
     * Check if the file path represents a valid BRIM file.
     */
    static boolean isBrimFile(String path) {
        String normalized = normalizePath(path);
        return normalized.endsWith(".brim.zarr") || normalized.endsWith(".brim.zip");
    }

    /**
     * Prompt user to select a BRIM file or folder.
     * @param path the path for the brim file. If null a file chooser will be shown
     * @return the path for the brim file, null in case of an error
     */
    static String promptForPath(String path){
        if (path == null || path.isEmpty()) {
            // If no path is passed as argument, open a dialog
            GenericDialog gd = new GenericDialog("Open BRIM");
            String[] options = {
                "File (.brim.zip)",
                "Folder (.brim.zarr)"
            };
            gd.addChoice("Select", options, options[0]);
            gd.showDialog();
            if (gd.wasCanceled()) {
                return null;
            }

            String choice = gd.getNextChoice();
            if (choice.startsWith("File")) {
                path = IJ.getFilePath("Open BRIM File");
            }
            else {
                DirectoryChooser dc = new DirectoryChooser("Open BRIM Folder");
                path = dc.getDirectory();
            }
            if (path == null) {
                return path; // User cancelled
            }
        }

        path = normalizePath(path);

        // Check if file exists and has valid extension
        File file = new File(path);
        if (!file.exists()) {
            String errorMsg = "File does not exist: " + path;
            IJ.error("BRIM File Opener", errorMsg);
            return null;
        }

        if (!FileInput.isBrimFile(path)) {
            String errorMsg = "Not a valid BRIM file or folder. Expected .brim.zarr or .brim.zip";
            IJ.error("BRIM File Opener", errorMsg);
            return null;
        }
        return path;
    }

    /**
     * Normalize a path by trimming trailing file separators.
     */
    static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        String normalized = path;
        while (normalized.endsWith(File.separator)) {
            normalized = normalized.substring(0, normalized.length() - File.separator.length());
        }
        return normalized;
    }
}
