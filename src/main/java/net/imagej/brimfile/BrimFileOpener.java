package net.imagej.brimfile;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import ij.gui.GenericDialog;

import org.apposed.appose.Environment;
import org.apposed.appose.Service;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * ImageJ plugin to open BRIM (Brillouin Imaging) files.
 * 
 * This plugin uses Apposed to interface with the Python brimfile package
 * to read .brim.zarr and .brim.zip files containing Brillouin microscopy data.
 */
public class BrimFileOpener implements PlugIn {

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

            GenericDialog gd = new GenericDialog("Select channels");
            for (String quantity : quantities) {
                gd.addCheckbox(quantity, false);
            }
            gd.showDialog();
            if (gd.wasCanceled()) {
                return null;
            }
            
            // TODO: check if there is a way of sharing variables between tasks rather than closing the file and opening it again here
            pythonCode = String.format(
                "import brimfile as brim\n" +
                "import numpy as np\n" +
                "\n" +
                "# Open the BRIM file\n" +
                "f = brim.File('%s')\n" +
                "# Get the first data group\n" +
                "d = f.get_data()\n" +
                "\n" +
                "# Get the first analysis results\n" +
                "ar = d.get_analysis_results()\n" +
                "\n" +
                "# Get the Brillouin shift image\n" +
                "Quantity = brim.Data.AnalysisResults.Quantity\n" +
                "PeakType = brim.Data.AnalysisResults.PeakType\n" +
                "img, px_size = ar.get_image(Quantity.Shift, PeakType.average)\n" +
                "\n" +
                "# Get metadata\n" +
                "md = d.get_metadata()\n" +
                "metadata_dict = md.all_to_dict()\n" +
                "\n" +
                "# Close the file\n" +
                "f.close()\n" +
                "\n" +
                "# Prepare result dictionary\n" +
                "task.outputs['image_shape'] = img.shape\n" +
                "task.outputs['image_data'] = img.flatten().tolist()\n" +
                "task.outputs['data_group_name'] = d.get_name()\n" +
                "task.outputs['ar_name'] = ar.get_name()\n" +
                "\n" +
                "# Parse pixel size\n" +
                "if px_size is not None:\n" +
                "    task.outputs['pixel_depth'] = float(px_size[0].value)\n" +
                "    task.outputs['pixel_height'] = float(px_size[1].value)\n" +
                "    task.outputs['pixel_width'] = float(px_size[2].value)\n" +
                "    task.outputs['pixel_unit'] = str(px_size[2].units)\n" +
                "else:\n" +
                "    task.outputs['pixel_depth'] = 1.0\n" +
                "    task.outputs['pixel_height'] = 1.0\n" +
                "    task.outputs['pixel_width'] = 1.0\n" +
                "    task.outputs['pixel_unit'] = 'um'\n",
                path.replace("\\", "\\\\")
            );         
            
            outputs = PyUtils.executePythonCode(python, pythonCode);

            IJ.showStatus("Converting to ImageJ format...");
            
            // Extract image data from task outputs
            @SuppressWarnings("unchecked")
            List<Number> imageData = (List<Number>) outputs.get("image_data");
            @SuppressWarnings("unchecked")
            List<Number> shapeList = (List<Number>) outputs.get("image_shape");
            
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
            ImagePlus imp = convertToImagePlus(imageData, nz, ny, nx, 
                pixelDepth, pixelHeight, pixelWidth, unit, 
                dataGroupName, arName, path);
            
            IJ.showStatus("BRIM file loaded successfully");
            return imp;            
        }
    }
    


    /**
     * Convert flattened image data to ImageJ ImagePlus.
     */
    private ImagePlus convertToImagePlus(List<Number> imageData, int nz, int ny, int nx,
                                         double pixelDepth, double pixelHeight, double pixelWidth, String unit,
                                         String dataGroupName, String arName, String path) {
        // Create ImageStack
        ImageStack stack = new ImageStack(nx, ny);
        
        // Convert flattened list to ImageJ stack
        for (int z = 0; z < nz; z++) {
            float[] pixels = new float[nx * ny];
            int baseIndex = z * nx * ny;
            
            for (int i = 0; i < nx * ny; i++) {
                pixels[i] = imageData.get(baseIndex + i).floatValue();
            }
            
            FloatProcessor fp = new FloatProcessor(nx, ny, pixels);
            stack.addSlice("z=" + (z + 1), fp);
        }
        
        // Create ImagePlus
        String title = new File(path).getName() + " - " + dataGroupName + " - Brillouin Shift";
        ImagePlus imp = new ImagePlus(title, stack);
        
        // Set calibration
        Calibration cal = imp.getCalibration();
        cal.pixelWidth = pixelWidth;
        cal.pixelHeight = pixelHeight;
        cal.pixelDepth = pixelDepth;
        cal.setUnit(unit);
        
        // Set display properties
        imp.setDisplayRange(imp.getStatistics().min, imp.getStatistics().max);
        
        return imp;
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
