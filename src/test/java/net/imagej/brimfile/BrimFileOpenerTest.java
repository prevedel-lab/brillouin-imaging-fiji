package net.imagej.brimfile;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.measure.Calibration;
import org.apposed.appose.NDArray;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for BrimFileOpener internals that are safe in headless mode.
 */
public class BrimFileOpenerTest {

    private Object invokePrivate(
        BrimFileOpener opener,
        String methodName,
        Class<?>[] parameterTypes,
        Object... args
    ) throws Exception {
        Method method = BrimFileOpener.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(opener, args);
    }

    private ImagePlus invokeConvertToImagePlus(
        BrimFileOpener opener,
        float[] imageData,
        int nt,
        int nc,
        int nz,
        int ny,
        int nx,
        double pixelDepth,
        double pixelHeight,
        double pixelWidth,
        String unit,
        String dataGroupName,
        String arName,
        String path,
        List<String> channelNames,
        boolean resampleXYForDisplay
    ) throws Exception {
        return (ImagePlus) invokePrivate(opener,
            "convertToImagePlus",
            new Class<?>[] {
                float[].class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                double.class,
                double.class,
                double.class,
                String.class,
                String.class,
                String.class,
                String.class,
                List.class,
                boolean.class
            },
            imageData,
            nt,
            nc,
            nz,
            ny,
            nx,
            pixelDepth,
            pixelHeight,
            pixelWidth,
            unit,
            dataGroupName,
            arName,
            path,
            channelNames,
            resampleXYForDisplay);
    }

    private float[] invokeReadImageDataFromSharedMemory(
        BrimFileOpener opener,
        Object imageDataObject,
        int nt,
        int nc,
        int nz,
        int ny,
        int nx
    ) throws Exception {
        return (float[]) invokePrivate(opener,
            "readImageDataFromSharedMemory",
            new Class<?>[] {
                Object.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class
            },
            imageDataObject,
            nt,
            nc,
            nz,
            ny,
            nx);
    }

    private NDArray createFloat32Array(int[] shapeCOrder, float[] values) {
        NDArray array = new NDArray(
            NDArray.DType.FLOAT32,
            new NDArray.Shape(NDArray.Shape.Order.C_ORDER, shapeCOrder));
        FloatBuffer buffer = array.buffer().asFloatBuffer();
        buffer.put(values);
        buffer.rewind();
        return array;
    }

    @Test
    public void testPluginInstantiation() {
        BrimFileOpener plugin = new BrimFileOpener();
        assertNotNull(plugin);
    }

    @Test
    public void testToPythonStringListLiteralEscapesCharacters() throws Exception {
        BrimFileOpener plugin = new BrimFileOpener();
        String literal = (String) invokePrivate(plugin,
            "toPythonStringListLiteral",
            new Class<?>[] { List.class },
            Arrays.asList("Shift", "A'B", "C\\D"));
        assertEquals("['Shift', 'A\\'B', 'C\\\\D']", literal);
    }

    @Test
    public void testReadResourceAsStringLoadsPythonTemplate() throws Exception {
        BrimFileOpener plugin = new BrimFileOpener();
        String script = (String) invokePrivate(plugin,
            "readResourceAsString",
            new Class<?>[] { String.class },
            "/script/load_brimfile.py");
        assertTrue(script.contains("selected_quantities = %s"));
        assertTrue(script.contains("task.outputs['image_data']"));
    }

    @Test
    public void testReadResourceAsStringMissingThrowsIOException() throws Exception {
        BrimFileOpener plugin = new BrimFileOpener();
        try {
            invokePrivate(plugin,
                "readResourceAsString",
                new Class<?>[] { String.class },
                "/script/does_not_exist.py");
            fail("Expected IOException for missing resource");
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof IOException);
        }
    }

    @Test
    public void testReadImageDataFromSharedMemory5D() throws Exception {
        BrimFileOpener plugin = new BrimFileOpener();
        float[] expected = new float[] { 1f, 2f, 3f, 4f };
        NDArray array = createFloat32Array(new int[] { 1, 1, 1, 2, 2 }, expected);

        float[] actual = invokeReadImageDataFromSharedMemory(plugin, array, 1, 1, 1, 2, 2);
        assertArrayEquals(expected, actual, 0f);
    }

    @Test
    public void testReadImageDataFromSharedMemoryLegacy4D() throws Exception {
        BrimFileOpener plugin = new BrimFileOpener();
        float[] expected = new float[] { 10f, 20f, 30f, 40f };
        NDArray array = createFloat32Array(new int[] { 1, 1, 2, 2 }, expected);

        float[] actual = invokeReadImageDataFromSharedMemory(plugin, array, 1, 1, 1, 2, 2);
        assertArrayEquals(expected, actual, 0f);
    }

    @Test
    public void testReadImageDataFromSharedMemoryRejectsWrongDtype() throws Exception {
        BrimFileOpener plugin = new BrimFileOpener();
        NDArray array = new NDArray(
            NDArray.DType.INT32,
            new NDArray.Shape(NDArray.Shape.Order.C_ORDER, 1, 1, 1, 1, 1));

        float[] actual = invokeReadImageDataFromSharedMemory(plugin, array, 1, 1, 1, 1, 1);
        assertNull(actual);
    }

    @Test
    public void testReadImageDataFromSharedMemoryRejectsWrongShape() throws Exception {
        BrimFileOpener plugin = new BrimFileOpener();
        NDArray array = createFloat32Array(new int[] { 1, 1, 1, 1 }, new float[] { 1f });

        float[] actual = invokeReadImageDataFromSharedMemory(plugin, array, 2, 1, 1, 1, 1);
        assertNull(actual);
    }

    @Test
    public void testReadImageDataFromSharedMemoryRejectsNonNdArray() throws Exception {
        BrimFileOpener plugin = new BrimFileOpener();
        float[] actual = invokeReadImageDataFromSharedMemory(plugin, "not-ndarray", 1, 1, 1, 1, 1);
        assertNull(actual);
    }

    @Test
    public void testConvertToImagePlusSetsHyperstackOrderAndCalibration() throws Exception {
        BrimFileOpener plugin = new BrimFileOpener();
        float[] imageData = new float[] { 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f };
        ImagePlus imp = invokeConvertToImagePlus(
            plugin,
            imageData,
            2,
            2,
            2,
            1,
            1,
            3.0,
            2.0,
            1.0,
            "um",
            "dg0",
            "ar0",
            "/tmp/example.brim.zip",
            Arrays.asList("Shift", "Width"),
            false);

        assertTrue(imp instanceof CompositeImage);
        assertEquals(2, imp.getNChannels());
        assertEquals(2, imp.getNSlices());
        assertEquals(2, imp.getNFrames());
        assertEquals("example.brim.zip - dg0 - ar0 - Channels", imp.getTitle());

        for (int t = 1; t <= 2; t++) {
            for (int z = 1; z <= 2; z++) {
                for (int c = 1; c <= 2; c++) {
                    int stackIndex = imp.getStackIndex(c, z, t);
                    float expected = (float) ((((t - 1) * 2 + (z - 1)) * 2 + (c - 1)) + 1);
                    float actual = imp.getStack().getProcessor(stackIndex).getf(0, 0);
                    assertEquals(expected, actual, 0f);
                }
            }
        }

        Calibration cal = imp.getCalibration();
        assertEquals(1.0, cal.pixelWidth, 1e-12);
        assertEquals(2.0, cal.pixelHeight, 1e-12);
        assertEquals(3.0, cal.pixelDepth, 1e-12);
        assertTrue("um".equals(cal.getUnit()) || "\u00b5m".equals(cal.getUnit()));
    }

    @Test
    public void testConvertToImagePlusUsesFallbackNamesForBlankGroupAndAr() throws Exception {
        BrimFileOpener plugin = new BrimFileOpener();
        ImagePlus imp = invokeConvertToImagePlus(
            plugin,
            new float[] { 42f },
            1,
            1,
            1,
            1,
            1,
            1.0,
            1.0,
            1.0,
            "um",
            null,
            "  ",
            "/tmp/single.brim.zarr",
            Collections.singletonList("Shift"),
            false);

        assertFalse(imp instanceof CompositeImage);
        assertEquals("single.brim.zarr - Data - Analysis - Shift", imp.getTitle());
    }

    @Test
    public void testConvertToImagePlusResamplesToSquarePixels() throws Exception {
        BrimFileOpener plugin = new BrimFileOpener();
        float[] imageData = new float[] { 1f, 2f, 3f, 4f, 5f, 6f };
        ImagePlus imp = invokeConvertToImagePlus(
            plugin,
            imageData,
            1,
            1,
            1,
            2,
            3,
            4.0,
            1.0,
            2.0,
            "um",
            "dg0",
            "ar0",
            "/tmp/resample.brim.zip",
            Collections.singletonList("Shift"),
            true);

        assertEquals(6, imp.getWidth());
        assertEquals(2, imp.getHeight());

        Calibration cal = imp.getCalibration();
        assertEquals(1.0, cal.pixelWidth, 1e-12);
        assertEquals(1.0, cal.pixelHeight, 1e-12);
        assertEquals(4.0, cal.pixelDepth, 1e-12);
    }

    @Test
    public void testConvertToImagePlusSkipsResampleWhenFactorTooLarge() throws Exception {
        BrimFileOpener plugin = new BrimFileOpener();
        float[] imageData = new float[] { 1f, 2f, 3f, 4f, 5f, 6f };
        ImagePlus imp = invokeConvertToImagePlus(
            plugin,
            imageData,
            1,
            1,
            1,
            2,
            3,
            4.0,
            1.0,
            20.0,
            "um",
            "dg0",
            "ar0",
            "/tmp/noresample.brim.zip",
            Collections.singletonList("Shift"),
            true);

        assertEquals(3, imp.getWidth());
        assertEquals(2, imp.getHeight());

        Calibration cal = imp.getCalibration();
        assertEquals(20.0, cal.pixelWidth, 1e-12);
        assertEquals(1.0, cal.pixelHeight, 1e-12);
        assertEquals(4.0, cal.pixelDepth, 1e-12);
    }

    @Test
    public void testConvertToImagePlusKeepsOriginalCalibrationWhenResampleDisabled() throws Exception {
        BrimFileOpener plugin = new BrimFileOpener();
        float[] imageData = new float[] { 1f, 2f, 3f, 4f, 5f, 6f };
        ImagePlus imp = invokeConvertToImagePlus(
            plugin,
            imageData,
            1,
            1,
            1,
            2,
            3,
            4.0,
            1.0,
            2.0,
            "um",
            "dg0",
            "ar0",
            "/tmp/noresample-flag.brim.zip",
            Collections.singletonList("Shift"),
            false);

        assertEquals(3, imp.getWidth());
        assertEquals(2, imp.getHeight());

        Calibration cal = imp.getCalibration();
        assertEquals(2.0, cal.pixelWidth, 1e-12);
        assertEquals(1.0, cal.pixelHeight, 1e-12);
        assertEquals(4.0, cal.pixelDepth, 1e-12);
    }
}
