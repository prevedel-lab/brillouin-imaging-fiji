# brillouin-imaging-fiji

A FIJI/ImageJ plugin to open and work with BRIM (Brillouin Imaging) files containing Brillouin microscopy data.

## What is it?

This plugin enables ImageJ/FIJI to read `.brim.zarr` and `.brim.zip` files, which contain spectral data and metadata from Brillouin microscopy. The plugin uses [Apposed](https://apposed.org/) to interface with the Python [brimfile package](https://github.com/prevedel-lab/brimfile) in a separate Python process.

At the current stage, this is a proof of concept to open a BRIM file and it doesn't allow to select the quantity (i.e. shift, width, etc...) and timelapses. More work is planned to extend it.

## Features

- Open BRIM files (`.brim.zarr` and `.brim.zip` formats)
- Display Brillouin shift images with proper pixel size, read from the metadata
- Automatic Python environment setup with brimfile dependency

## Installation

### Prerequisites

1. **FIJI/ImageJ**: Download and install [FIJI](https://fiji.sc/) or [ImageJ](https://imagej.net/)
   - **Compatibility**: This plugin is fully compatible with FIJI stable and requires **Java 8 or higher**
   - The plugin works with both FIJI stable (Java 8) and FIJI latest (Java 11+)

### Plugin Installation

#### Option 1: Install from Release (Recommended)

1. Go to the [Releases page](https://github.com/prevedel-lab/brillouin-imaging-fiji/releases)
2. Download the latest `brillouin-imaging-fiji-*.jar` file
3. Copy the JAR file to your FIJI/ImageJ plugins folder
4. Restart FIJI/ImageJ

On first use, the plugin will automatically:
- Download and set up a Python virtual environment using uv
- Install the brimfile package and its dependencies
- This may take a few minutes on first run but is automatic

#### Option 2: Build from Source

1. Build the plugin:
   ```bash
   mvn clean package
   ```

2. Copy the generated JAR file from `target/brillouin-imaging-fiji-0.1.0-SNAPSHOT.jar` to your FIJI/ImageJ plugins folder

3. Restart FIJI/ImageJ

## Usage

### Opening BRIM Files

1. In FIJI/ImageJ, go to **File > Import > BRIM File (.brim.zarr, .brim.zip)...**
2. Select your BRIM file in the file chooser
3. The plugin will load the Brillouin shift image and display it

The plugin automatically:
- Loads the first data group in the BRIM file
- Extracts the first analysis results
- Displays the Brillouin shift image with proper calibration
- Applies appropriate display range based on image statistics

### Supported File Formats

- `.brim.zarr` - BRIM files stored as Zarr directories
- `.brim.zip` - BRIM files stored as ZIP archives

## Development

### Building from Source

```bash
# Clone the repository
git clone https://github.com/prevedel-lab/brillouin-imaging-fiji.git
cd brillouin-imaging-fiji

# Build with Maven
mvn clean package
```

### Project Structure

```
brillouin-imaging-fiji/
├── pom.xml                                    # Maven configuration
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── net/imagej/brimfile/
│   │   │       └── BrimFileOpener.java       # Main plugin class
│   │   └── resources/
│   │       └── plugins.config                # Plugin registration
│   └── test/
│       └── java/                             # Unit tests
└── README.md
```

### Continuous Integration

The project uses GitHub Actions for automated building and releasing:

- **Build on Push/PR**: The JAR is automatically built and tested on every push to the main branch and on pull requests
- **Automated Releases**: When a tag is pushed (e.g., `v1.0.0`), a GitHub release is automatically created with the JAR file attached

To create a new release:
```bash
git tag v1.0.0
git push origin v1.0.0
```

The JAR artifacts are also available as build artifacts on GitHub Actions for each successful build.


### Dependencies

- **ImageJ**: Core ImageJ functionality
- **Apposed**: Python integration library that manages Python environments and executes Python code in a separate process
- **brimfile**: Python package for reading/writing BRIM files (automatically installed by Apposed)

## Technical Details

The plugin uses Apposed to:
1. Create and manage a Python virtual environment with uv
2. Automatically install the brimfile package and dependencies
3. Execute Python scripts in a separate process
4. Transfer data between Java and Python efficiently
5. Convert NumPy arrays to ImageJ ImagePlus objects

This approach provides:
- **Automatic setup**: No manual Python installation or configuration required
- **Isolated environments**: Python dependencies don't conflict with system Python
- **Robust execution**: Python runs in a separate process for better reliability
- **Fast and lightweight**: Uses standard Python venv via uv (no conda needed)
- **Easy maintenance**: Python environment and packages are managed automatically

The Python environment is cached in `~/.local/share/appose/envs/brimfile` and reused across sessions.

## Related Projects

- [brimfile](https://github.com/prevedel-lab/brimfile) - Python package for reading/writing BRIM files
- [brillouin-imaging-napari](https://github.com/prevedel-lab/brillouin-imaging-napari) - Napari plugin for BRIM files
- [Brillouin Standard File](https://github.com/prevedel-lab/Brillouin-standard-file) - BRIM file format specification

## License

LGPL-3.0 License

## Support

For issues, please open a [GitHub issue](https://github.com/prevedel-lab/brillouin-imaging-fiji/issues).
