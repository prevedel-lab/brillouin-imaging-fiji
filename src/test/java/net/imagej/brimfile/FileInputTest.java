package net.imagej.brimfile;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for BRIM file path handling helpers.
 */
public class FileInputTest {

    @Test
    public void testNormalizePathRemovesTrailingSeparators() {
        String normalized = FileInput.normalizePath("/tmp/test.brim.zip" + File.separator + File.separator);
        assertEquals("/tmp/test.brim.zip", normalized);
    }

    @Test
    public void testIsBrimFileRecognizesSupportedExtensions() {
        assertTrue(FileInput.isBrimFile("/tmp/data.brim.zip"));
        assertTrue(FileInput.isBrimFile("/tmp/data.brim.zarr" + File.separator));
        assertFalse(FileInput.isBrimFile("/tmp/data.tif"));
    }

    @Test
    public void testPromptForPathAcceptsExistingBrimZipFile() throws IOException {
        Path path = Files.createTempFile("brim-test-", ".brim.zip");
        try {
            String result = FileInput.promptForPath(path.toString());
            assertEquals(path.toString(), result);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void testPromptForPathAcceptsExistingBrimZarrDirectoryWithTrailingSlash() throws IOException {
        Path baseDir = Files.createTempDirectory("brim-test-dir-");
        Path zarrDir = baseDir.resolve("dataset.brim.zarr");
        Files.createDirectory(zarrDir);

        try {
            String result = FileInput.promptForPath(zarrDir.toString() + File.separator);
            assertNotNull(result);
            assertEquals(zarrDir.toString(), result);
        } finally {
            Files.deleteIfExists(zarrDir);
            Files.deleteIfExists(baseDir);
        }
    }
}
