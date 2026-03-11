package net.imagej.brimfile;

import org.apposed.appose.Environment;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.FloatBuffer;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Integration tests for the Java-Python bridge via Appose.
 *
 * Run with the integration-tests profile:
 * mvn -Pintegration-tests verify
 */
public class PyUtilsIT {

    private static final String ENABLE_PROPERTY = "brim.integration.tests";
    private static Environment env;

    @BeforeClass
    public static void setUpEnvironment() {
        Assume.assumeTrue(
            "Integration tests disabled. Use -D" + ENABLE_PROPERTY + "=true",
            Boolean.parseBoolean(System.getProperty(ENABLE_PROPERTY, "false")));

        env = PyUtils.getOrCreateBrimfileEnvironment();
        assertNotNull(env);
    }

    @Test(timeout = 600000)
    public void testExecutePythonCodeReturnsOutputs() throws Exception {
        try (Service python = env.python()) {
            Map<String, Object> outputs = PyUtils.executePythonCode(
                python,
                "task.outputs['sum'] = 2 + 3\n" +
                "task.outputs['msg'] = 'ok'\n");

            assertEquals(5, ((Number) outputs.get("sum")).intValue());
            assertEquals("ok", outputs.get("msg"));
        }
    }

    @Test(timeout = 600000)
    public void testExecutePythonCodePropagatesErrors() throws Exception {
        try (Service python = env.python()) {
            try {
                PyUtils.executePythonCode(python, "raise RuntimeError('boom')");
                fail("Expected exception from Python failure");
            } catch (Exception e) {
                String message = e.getMessage();
                assertNotNull(message);
                assertTrue(
                    message.contains("Python execution failed") ||
                    message.contains("RuntimeError") ||
                    message.contains("boom"));
            }
        }
    }

    @Test(timeout = 600000)
    public void testSharedMemoryNdArrayRoundTrip() throws Exception {
        try (Service python = env.python()) {
            Map<String, Object> outputs = PyUtils.executePythonCode(
                python,
                "import appose\n" +
                "import numpy as np\n" +
                "arr = appose.NDArray('float32', [1, 1, 1, 2, 2])\n" +
                "view = arr.ndarray()\n" +
                "view[:] = np.array([[[[[1.0, 2.0], [3.0, 4.0]]]]], dtype=np.float32)\n" +
                "task.outputs['arr'] = arr\n");

            Object arrObject = outputs.get("arr");
            assertTrue(arrObject instanceof NDArray);

            try (NDArray array = (NDArray) arrObject) {
                assertEquals(NDArray.DType.FLOAT32, array.dType());
                assertArrayEquals(
                    new int[] { 1, 1, 1, 2, 2 },
                    array.shape().toIntArray(NDArray.Shape.Order.C_ORDER));

                FloatBuffer floatBuffer = array.buffer().asFloatBuffer();
                float[] actual = new float[4];
                floatBuffer.get(actual);
                assertArrayEquals(new float[] { 1f, 2f, 3f, 4f }, actual, 0f);
            }
        }
    }

    @Test(timeout = 600000)
    public void testGetBrimfileVersionFromEnvironment() throws Exception {
        try (Service python = env.python()) {
            String version = PyUtils.getBrimfileVersion(python);
            assertNotNull(version);
            assertFalse(version.trim().isEmpty());
            assertFalse("unknown".equalsIgnoreCase(version.trim()));
        }
    }
}
