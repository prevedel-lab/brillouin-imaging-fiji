package net.imagej.brimfile;

import ij.IJ;
import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import ij.gui.GenericDialog;

import org.apposed.appose.Environment;
import org.apposed.appose.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ImageJ plugin to open BRIM (Brillouin Imaging) files.
 * 
 * This plugin uses Apposed to interface with the Python brimfile package
 * to read .brim.zarr and .brim.zip files containing Brillouin microscopy data.
 */
public class BrimFileOpener implements PlugIn {

    private static final AtomicBoolean BRIMFILE_VERSION_LOGGED = new AtomicBoolean(false);
    private static final double MAX_DISPLAY_RESAMPLE_FACTOR = 8.0;
    private static final String DEFAULT_LUT_COMMAND = "mpl-inferno";

    @Override
    public void run(String arg) {
        String path = arg;
        // If arg is empty, show file chooser
        path = FileInput.promptForPath(path);   
        if (path == null) {
            return;
        }

        // Open and display the BRIM file
        try {
            ImagePlus imp = openBrimFile(path);
            if (imp != null) {
                imp.show();
            }
        } catch (Exception e) {
            IJ.error("BRIM File Opener", "Error opening BRIM file:\n" + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Open a BRIM file using the Python brimfile package via Apposed.
     * 
     * @param path Path to the BRIM file
     * @return ImagePlus object containing the Brillouin shift image
     */
    private ImagePlus openBrimFile(String path) throws Exception {
        IJ.showStatus("Opening BRIM file...");
        
        // Create or get a Python environment with brimfile installed
        Environment env = PyUtils.getOrCreateBrimfileEnvironment();
        
        try (Service python = env.python()) {
            logBrimfileVersionIfNeeded(python);
            IJ.showStatus("Loading BRIM file...");   
            
            String pythonCode = String.format(
                "import brimfile as brim\n" +
                "import numpy as np\n" +
                "\n" +
                "# Open the BRIM file\n" +
                "f = brim.File('%s')\n" +
                "\n" +
                "# Get the first data group\n" +
                "d = f.get_data()\n" +
                "\n" +
                "# Get the first analysis results\n" +
                "ar = d.get_analysis_results()\n" +
                "\n" +
                "# Get the existing quantity of the first group and assume they all have the same\n" +
                "existing_quantities = ar.list_existing_quantities()\n" +
                "f.close()" +
                "\n" +
                "qts = [qt.name for qt in existing_quantities]" +
                "\n" +
                "task.outputs['quantities'] = qts" ,
                path.replace("\\", "\\\\")
            );
            Map<String, Object> outputs = PyUtils.executePythonCode(python, pythonCode);
            @SuppressWarnings("unchecked")
            List<String> quantities = (List<String>) outputs.get("quantities");

            GenericDialog channels_selection_dialog = new GenericDialog("Select channels");
            for (String quantity : quantities) {
                channels_selection_dialog.addCheckbox(quantity, false);
            }
            channels_selection_dialog.showDialog();
            if (channels_selection_dialog.wasCanceled()) {
                return null;
            }

            List<String> selectedQuantities = new ArrayList<String>();
            for (String quantity : quantities) {
                if (channels_selection_dialog.getNextBoolean()) {
                    selectedQuantities.add(quantity);
                }
            }

            if (selectedQuantities.isEmpty()) {
                IJ.error("BRIM File Opener", "No channels selected.");
                return null;
            }

            String selectedQuantitiesLiteral = toPythonStringListLiteral(selectedQuantities);
            
            // TODO: check if there is a way of sharing variables between tasks rather than closing the file and opening it again here
            pythonCode = String.format(
                "import brimfile as brim\n" +
                "import numpy as np\n" +
                "\n" +
                "# Open the BRIM file\n" +
                "f = brim.File('%s')\n" +
                "selected_quantities = %s\n" +
                "\n" +
                "# Define outputs in case loading fails\n" +
                "task.outputs['image_shape'] = [0, 0, 0]\n" +
                "task.outputs['image_data'] = []\n" +
                "task.outputs['channel_names'] = []\n" +
                "task.outputs['num_channels'] = 0\n" +
                "task.outputs['channel_errors'] = []\n" +
                "\n" +
                "try:\n" +
                "    # Get the first data group\n" +
                "    d = f.get_data()\n" +
                "\n" +
                "    # Get the first analysis results\n" +
                "    ar = d.get_analysis_results()\n" +
                "\n" +
                "    # Get selected channel images\n" +
                "    Quantity = brim.Data.AnalysisResults.Quantity\n" +
                "    PeakType = brim.Data.AnalysisResults.PeakType\n" +
                "    channel_images = []\n" +
                "    channel_names = []\n" +
                "    channel_errors = []\n" +
                "    image_shape = None\n" +
                "    px_size_ref = None\n" +
                "\n" +
                "    for quantity_name in selected_quantities:\n" +
                "        try:\n" +
                "            quantity_enum = getattr(Quantity, quantity_name)\n" +
                "        except AttributeError:\n" +
                "            channel_errors.append(f'Unknown quantity: {quantity_name}')\n" +
                "            continue\n" +
                "\n" +
                "        try:\n" +
                "            img, px_size = ar.get_image(quantity_enum, PeakType.average)\n" +
                "        except Exception as e:\n" +
                "            channel_errors.append(f'Failed to load {quantity_name}: {e}')\n" +
                "            continue\n" +
                "\n" +
                "        if img is None:\n" +
                "            channel_errors.append(f'No image data for {quantity_name}')\n" +
                "            continue\n" +
                "\n" +
                "        if image_shape is None:\n" +
                "            image_shape = img.shape\n" +
                "            px_size_ref = px_size\n" +
                "        elif img.shape != image_shape:\n" +
                "            channel_errors.append(f'Incompatible shape for {quantity_name}: {img.shape} != {image_shape}')\n" +
                "            continue\n" +
                "\n" +
                "        channel_names.append(quantity_name)\n" +
                "        channel_images.append(img)\n" +
                "\n" +
                "    if image_shape is not None and channel_images:\n" +
                "        nz, ny, nx = image_shape\n" +
                "        stack_data = []\n" +
                "        for z in range(nz):\n" +
                "            for channel_img in channel_images:\n" +
                "                stack_data.extend(channel_img[z].flatten().tolist())\n" +
                "    else:\n" +
                "        stack_data = []\n" +
                "        image_shape = (0, 0, 0)\n" +
                "\n" +
                "    data_group_name = d.get_name()\n" +
                "    ar_name = ar.get_name()\n" +
                "\n" +
                "    task.outputs['image_shape'] = list(image_shape)\n" +
                "    task.outputs['image_data'] = stack_data\n" +
                "    task.outputs['data_group_name'] = data_group_name\n" +
                "    task.outputs['ar_name'] = ar_name\n" +
                "    task.outputs['channel_names'] = channel_names\n" +
                "    task.outputs['num_channels'] = len(channel_names)\n" +
                "    task.outputs['channel_errors'] = channel_errors\n" +
                "\n" +
                "    # Parse pixel size\n" +
                "    if px_size_ref is not None:\n" +
                "        task.outputs['pixel_depth'] = float(px_size_ref[0].value)\n" +
                "        task.outputs['pixel_height'] = float(px_size_ref[1].value)\n" +
                "        task.outputs['pixel_width'] = float(px_size_ref[2].value)\n" +
                "        task.outputs['pixel_unit'] = str(px_size_ref[2].units)\n" +
                "    else:\n" +
                "        task.outputs['pixel_depth'] = 1.0\n" +
                "        task.outputs['pixel_height'] = 1.0\n" +
                "        task.outputs['pixel_width'] = 1.0\n" +
                "        task.outputs['pixel_unit'] = 'um'\n" +
                "finally:\n" +
                "    f.close()\n",
                path.replace("\\", "\\\\"),
                selectedQuantitiesLiteral
            );         
            
            outputs = PyUtils.executePythonCode(python, pythonCode);

            IJ.showStatus("Converting to ImageJ format...");
            
            // Extract image data from task outputs
            @SuppressWarnings("unchecked")
            List<Number> imageData = (List<Number>) outputs.get("image_data");
            @SuppressWarnings("unchecked")
            List<Number> shapeList = (List<Number>) outputs.get("image_shape");
            @SuppressWarnings("unchecked")
            List<String> channelNames = (List<String>) outputs.get("channel_names");
            @SuppressWarnings("unchecked")
            List<String> channelErrors = (List<String>) outputs.get("channel_errors");

            int nc = ((Number) outputs.get("num_channels")).intValue();

            if (channelNames == null) {
                channelNames = new ArrayList<String>();
            }

            if (channelErrors != null && !channelErrors.isEmpty()) {
                IJ.log("BRIM File Opener: some selected channels were skipped:");
                for (String channelError : channelErrors) {
                    IJ.log("  - " + channelError);
                }
            }

            if (nc <= 0 || shapeList == null || shapeList.size() < 3 || imageData == null || imageData.isEmpty()) {
                IJ.error("BRIM File Opener", "None of the selected channels could be loaded.");
                return null;
            }
            
            int nz = shapeList.get(0).intValue();
            int ny = shapeList.get(1).intValue();
            int nx = shapeList.get(2).intValue();
            
            String dataGroupName = (String) outputs.get("data_group_name");
            String arName = (String) outputs.get("ar_name");
            
            double pixelDepth = ((Number) outputs.get("pixel_depth")).doubleValue();
            double pixelHeight = ((Number) outputs.get("pixel_height")).doubleValue();
            double pixelWidth = ((Number) outputs.get("pixel_width")).doubleValue();
            String unit = (String) outputs.get("pixel_unit");
            
            // Convert to ImageJ ImagePlus
            ImagePlus imp = convertToImagePlus(imageData, nc, nz, ny, nx,
                pixelDepth, pixelHeight, pixelWidth, unit, 
                dataGroupName, arName, path, channelNames);
            
            IJ.showStatus("BRIM file loaded successfully");
            return imp;            
        }
    }

    private void logBrimfileVersionIfNeeded(Service python) {
        if (BRIMFILE_VERSION_LOGGED.get()) {
            return;
        }

        String version = PyUtils.getBrimfileVersion(python);
        String message = "BRIM File Opener using brimfile Python package version: " + version;

        IJ.log(message);
        System.out.println(message);
        BRIMFILE_VERSION_LOGGED.set(true);
    }
    


    /**
     * Convert flattened image data to ImageJ ImagePlus.
     */
    private ImagePlus convertToImagePlus(List<Number> imageData, int nc, int nz, int ny, int nx,
                                         double pixelDepth, double pixelHeight, double pixelWidth, String unit,
                                         String dataGroupName, String arName, String path, List<String> channelNames) {
        // Create ImageStack
        ImageStack stack = new ImageStack(nx, ny);
        
        // Convert flattened list to ImageJ hyperstack order: C changes fastest, then Z.
        for (int z = 0; z < nz; z++) {
            for (int c = 0; c < nc; c++) {
                float[] pixels = new float[nx * ny];
                int baseIndex = (z * nc + c) * nx * ny;

                for (int i = 0; i < nx * ny; i++) {
                    pixels[i] = imageData.get(baseIndex + i).floatValue();
                }

                FloatProcessor fp = new FloatProcessor(nx, ny, pixels);
                String channelName = c < channelNames.size() ? channelNames.get(c) : "ch" + (c + 1);
                stack.addSlice(channelName + " z=" + (z + 1), fp);
            }
        }
        
        // Create ImagePlus
        String channelTitle = (nc == 1 && !channelNames.isEmpty()) ? channelNames.get(0) : "Channels";
        String title = new File(path).getName() + " - " + dataGroupName + " - " + arName + " - " + channelTitle;
        ImagePlus imp = new ImagePlus(title, stack);

        imp.setDimensions(nc, nz, 1);
        imp.setOpenAsHyperStack(true);
        if (nc > 1) {
            imp = new CompositeImage(imp, CompositeImage.GRAYSCALE);
        }
        
        // Set calibration
        Calibration cal = imp.getCalibration();
        cal.pixelWidth = pixelWidth;
        cal.pixelHeight = pixelHeight;
        cal.pixelDepth = pixelDepth;
        cal.setUnit(unit);
        
        // Set display properties
        imp.setDisplayRange(imp.getStatistics().min, imp.getStatistics().max);
        applyDefaultLut(imp);
        
        return imp;
    }

    private void applyDefaultLut(ImagePlus imp) {
        try {
            IJ.run(imp, DEFAULT_LUT_COMMAND, "");
        } catch (Exception e) {
            // If the specified LUT command is not found, log a warning and continue without applying a LUT
            IJ.log("BRIM File Opener: LUT '" + DEFAULT_LUT_COMMAND + "' not found; using default LUT.");
        }
    }

    /**
     * Resample the XY plane so display proportions match physical pixel size.
     * The field of view is preserved by adjusting dimensions and setting square XY calibration.
     */
    private ImagePlus resampleXYToSquarePixels(ImagePlus imp) {
        Calibration cal = imp.getCalibration();
        if (cal == null) {
            return imp;
        }

        double pixelWidth = cal.pixelWidth;
        double pixelHeight = cal.pixelHeight;
        if (pixelWidth <= 0 || pixelHeight <= 0) {
            return imp;
        }

        // if the ratio is close enough to 1, skip resampling to avoid unnecessary interpolation
        double ratio = pixelWidth / pixelHeight;
        if (Math.abs(ratio - 1.0) < 1e-9) {
            return imp;
        }

        // if the scale factor is larger than MAX_DISPLAY_RESAMPLE_FACTOR, skip resampling
        double scaleFactor = ratio >= 1.0 ? ratio : 1.0 / ratio;
        if (scaleFactor > MAX_DISPLAY_RESAMPLE_FACTOR) {
            IJ.log(String.format(
                "BRIM File Opener: skipped XY display resampling because anisotropy factor %.3f exceeds max %.1f",
                scaleFactor, MAX_DISPLAY_RESAMPLE_FACTOR));
            return imp;
        }

        int originalWidth = imp.getWidth();
        int originalHeight = imp.getHeight();
        int targetWidth = originalWidth;
        int targetHeight = originalHeight;

        if (ratio > 1.0) {
            targetWidth = Math.max(1, (int) Math.round(originalWidth * ratio));
        } else {
            targetHeight = Math.max(1, (int) Math.round(originalHeight / ratio));
        }

        ImageStack sourceStack = imp.getStack();
        ImageStack resizedStack = new ImageStack(targetWidth, targetHeight);
        for (int i = 1; i <= sourceStack.getSize(); i++) {
            ImageProcessor ip = sourceStack.getProcessor(i);
            ip.setInterpolationMethod(ImageProcessor.BILINEAR);
            ImageProcessor resized = ip.resize(targetWidth, targetHeight);
            resizedStack.addSlice(sourceStack.getSliceLabel(i), resized);
        }

        ImagePlus resizedImp = new ImagePlus(imp.getTitle(), resizedStack);
        resizedImp.setDimensions(imp.getNChannels(), imp.getNSlices(), imp.getNFrames());
        resizedImp.setOpenAsHyperStack(imp.isHyperStack());
        resizedImp.setPosition(imp.getChannel(), imp.getSlice(), imp.getFrame());

        Calibration resizedCal = cal.copy();
        double squarePixelSize = Math.min(pixelWidth, pixelHeight);
        resizedCal.pixelWidth = squarePixelSize;
        resizedCal.pixelHeight = squarePixelSize;
        resizedImp.setCalibration(resizedCal);

        return resizedImp;
    }

    private String toPythonStringListLiteral(List<String> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            String escaped = values.get(i).replace("\\", "\\\\").replace("'", "\\'");
            builder.append("'").append(escaped).append("'");
        }
        builder.append("]");
        return builder.toString();
    }

    /**
     * Main method for testing the plugin standalone.
     */
    public static void main(String[] args) {
        // For testing purposes
        if (args.length > 0) {
            new BrimFileOpener().run(args[0]);
        } else {
            System.err.println("Usage: java BrimFileOpener <path-to-brim-file>");
        }
    }
}
