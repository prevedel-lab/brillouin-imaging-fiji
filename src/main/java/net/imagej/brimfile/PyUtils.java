package net.imagej.brimfile;

import java.io.File;
import java.util.Map;

import org.apposed.appose.Appose;
import org.apposed.appose.Environment;
import org.apposed.appose.Service;

/**
 * Helper utilities for setting up a Python environment and calling Python code.
 */
final class PyUtils {

    private PyUtils() {
        // Utility class
    }

    /**
     * Get or create a Python environment with brimfile installed.
     * Uses Apposed to manage the environment with uv (standard Python venv).
     * 
     * @return Environment with brimfile package installed
     * @throws IllegalStateException if environment cannot be created
     */
    static Environment getOrCreateBrimfileEnvironment() {
        try {
            // Get user home directory
            String userHome = System.getProperty("user.home");
            if (userHome == null || userHome.isEmpty()) {
                throw new IllegalStateException("Unable to determine user home directory");
            }
            
            // Create environment with brimfile dependency using uv (standard Python venv)
            // This will create an environment in ~/.local/share/appose/envs/brimfile
            File baseDir = new File(userHome, ".local/share/appose/envs/brimfile");
            
            System.out.println("Python environment path " + baseDir.getAbsolutePath());            
            return Appose.uv()
                .base(baseDir.getAbsolutePath())
                .python("3.11")
                .include("brimfile", "appose")
                .subscribeOutput(line -> System.out.println("Appose.uv output: " + line))
                .subscribeError(line -> System.out.println("Appose.uv error: " + line))
                .build();
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to create Python environment for brimfile. " +
                "Please check your internet connection and try again. " +
                "Error: " + e.getMessage(), e);
        }
    }

    /**
     * Execute Python code and return the task outputs.
     * 
     * @param python The Python service to execute code in
     * @param pythonCode Python code to execute
     * @return Map containing the task outputs
     * @throws Exception if execution fails
     */
    static Map<String, Object> executePythonCode(Service python, String pythonCode) throws Exception {
        Service.Task task = python.task(pythonCode).waitFor();
        
        if (task.status == Service.TaskStatus.COMPLETE) {
            return task.outputs;
        } else {
            String errorMsg = "Python execution failed";
            if (task.error != null && !task.error.isEmpty()) {
                errorMsg += ": " + task.error;
            }
            throw new Exception(errorMsg);
        }
    }
    
}
