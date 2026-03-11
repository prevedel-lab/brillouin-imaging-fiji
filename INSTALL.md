# BRIM File Plugin for ImageJ/FIJI - Installation Guide

This guide provides step-by-step instructions for installing and using the BRIM file plugin for ImageJ/FIJI.

## Prerequisites

### Install FIJI or ImageJ

Download and install FIJI or ImageJ:
- **FIJI (recommended)**: https://fiji.sc/
- **ImageJ**: https://imagej.net/

**Java Compatibility**: This plugin is fully compatible with **Java 8 or higher**. It works with:
- FIJI stable (Java 8)
- FIJI latest (Java 11+)  
- Any ImageJ installation running Java 8 or newer

**That's it!** No Python installation or configuration is required. The plugin will automatically set up its own Python environment with all necessary dependencies on first use.

## Installing the Plugin

### Option 1: Using a Pre-built Release (Recommended)

1. Download the latest JAR file from the [Releases](https://github.com/prevedel-lab/brillouin-imaging-fiji/releases) page

2. Locate your FIJI/ImageJ plugins folder:
   - **FIJI**: `Fiji.app/plugins/`
   - **ImageJ**: `ImageJ/plugins/`

3. Copy the plugin JAR file to the plugins folder:
   ```bash
   # Example for FIJI on Mac/Linux
   cp brillouin-imaging-fiji-*.jar /path/to/Fiji.app/plugins/
   
   # Example for Windows
   copy brillouin-imaging-fiji-*.jar C:\path\to\Fiji.app\plugins\
   ```

4. Restart FIJI/ImageJ

### Option 2: Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/prevedel-lab/brillouin-imaging-fiji.git
   cd brillouin-imaging-fiji
   ```

2. Build with Maven:
   ```bash
   mvn clean package
   ```
   
   This creates `target/brillouin-imaging-fiji-0.1.0-SNAPSHOT.jar`

3. Copy the JAR to your FIJI/ImageJ plugins folder (see Option 1, step 2-3)

4. Restart FIJI/ImageJ

## Usage

### Opening BRIM Files

1. Launch FIJI/ImageJ

2. Go to **File > Import > BRIM File (.brim.zarr, .brim.zip)...**

3. Select your BRIM file in the file chooser dialog

4. **First Time Only**: The plugin will automatically:
   - Download uv (Python package manager)
   - Create a Python virtual environment
   - Install the brimfile package and its dependencies
   - This may take a few minutes but only happens once

5. The plugin will then:
   - Read available quantities and ask you which channels to load
   - Load selected quantities across data groups (using the first analysis result per group)
   - Stack compatible groups as timepoints in one hyperstack
   - Display metadata in a `BRIM Metadata` text window
   - Show the calibrated result with an inferno LUT

### Subsequent Uses

After the first run, opening BRIM files is fast because the Python environment is already set up and cached.

### Supported File Formats

- `.brim.zarr` - BRIM files stored as Zarr directories
- `.brim.zip` - BRIM files stored as ZIP archives

### Drag and Drop

- Dragging a `.brim.zip` file or `.brim.zarr` folder into FIJI is supported.
- The BRIM drag-and-drop handler is installed automatically the first time you run **File > Import > BRIM File (.brim.zarr, .brim.zip)...**.
- You can also enable it manually via **File > Import > Enable BRIM Drag-and-Drop**.
- The handler installation is session-scoped and must be re-enabled after restarting FIJI.

## Troubleshooting

### First-time setup takes a long time

**This is normal!** On first use, the plugin needs to:
- Download uv (~20 MB)
- Create a Python virtual environment
- Download and install brimfile and its dependencies (~100-200 MB)

This only happens once. Subsequent uses are fast.

### Error: "Failed to load BRIM file"

**Possible causes**:
1. The BRIM file may be corrupted
2. The file may not contain the expected data structure
3. Network issues during first-time environment setup

**Solutions**:
- Verify the BRIM file is valid
- Check your internet connection (required for first-time setup)
- Try deleting `~/.local/share/appose/envs/brimfile` to force environment recreation

### Plugin doesn't appear in the menu

**Solution**:
1. Check that the JAR file is in the correct plugins folder
2. Restart FIJI/ImageJ
3. Check the ImageJ console for any error messages

### Out of Memory Errors

**Solution**: Increase FIJI/ImageJ memory allocation:
1. Edit `Fiji.app/ImageJ.cfg` (or similar)
2. Increase the `-Xmx` parameter (e.g., `-Xmx4096m` for 4GB)

## Testing the Installation

To verify everything is working:

1. Download a sample BRIM file (if available)
2. Open it using the plugin (File > Import > BRIM File)
3. Wait for first-time environment setup if needed
4. Check that the image displays correctly with proper calibration

## Python Environment Details

The plugin automatically manages a Python environment in:
- **Location**: `~/.local/share/appose/envs/brimfile`
- **Manager**: uv (fast Python package manager using standard venv)
- **Python version**: 3.11+
- **Packages**: brimfile, appose, and their dependencies

You can safely delete this directory to force a fresh installation if needed.

## Getting Help

- **Issues**: Report bugs at https://github.com/prevedel-lab/brillouin-imaging-fiji/issues
- **brimfile package**: https://github.com/prevedel-lab/brimfile
- **BRIM format specification**: https://github.com/prevedel-lab/Brillouin-standard-file
- **Apposed**: https://github.com/apposed/appose-java

## Performance Optimization

For better performance with large BRIM files:
1. Increase JVM heap size in ImageJ.cfg
2. Use local BRIM files instead of remote ones
3. Consider converting large .brim.zip files to .brim.zarr for faster access
4. Ensure you have adequate disk space for the Python environment cache

## Development

For developers who want to extend or modify the plugin:

1. Import the project into your IDE (IntelliJ IDEA, Eclipse, etc.)
2. Ensure Maven is configured
3. Build and test using Maven: `mvn clean test package`
4. Java 8+ is required for compilation

See [README.md](README.md) for more details on the project structure and development.
