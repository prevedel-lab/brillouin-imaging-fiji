# brillouin-imaging-fiji

A FIJI/ImageJ plugin to open and work with BRIM (Brillouin Imaging) files containing Brillouin microscopy data.

## What is it?

This plugin enables ImageJ/FIJI to read `.brim.zarr` and `.brim.zip` files, which contain spectral data and metadata from Brillouin microscopy. The plugin uses [Apposed](https://apposed.org/) to interface with the Python [brimfile package](https://github.com/prevedel-lab/brimfile) in a separate Python process.

The plugin supports selecting one or more quantities (for example Shift and Width), loading compatible data groups as timepoints, and displaying the result as an ImageJ hyperstack. It also supports drag-and-drop opening of BRIM files and folders in the FIJI window. The first analysis result in each data group is currently used.

## Features

- Open BRIM files (`.brim.zarr` and `.brim.zip` formats)
- Select one or more quantities to load as channels
- Stack compatible data groups as timepoints in a single hyperstack
- Show BRIM metadata in a separate ImageJ text window
- Preserve voxel calibration and optionally resample XY to square pixels for display
- Support drag-and-drop opening for BRIM files and folders
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
3. Select one or more quantities in the channel selection dialog
4. Optionally keep **Resample XY to square pixels** enabled for physically proportioned display
5. The plugin loads selected quantities and opens the result as an ImageJ hyperstack

### Drag And Drop

- Dragging a `.brim.zip` file or `.brim.zarr` folder into the FIJI main window is supported.
- The BRIM drag-and-drop handler is installed automatically the first time you run **File > Import > BRIM File (.brim.zarr, .brim.zip)...**.
- You can also install it explicitly via **File > Import > Enable BRIM Drag-and-Drop**.
- The installation needs to be done again if you restart FIJI.

The plugin automatically:
- Reads available quantities from the first data group
- Loads selected quantities across data groups using the first analysis result of each group
- Stacks compatible groups as timepoints (T)
- Displays metadata in a `BRIM Metadata` text window
- Preserves calibration and applies an inferno LUT for display
- Logs skipped channels or groups (for example, due to shape mismatch) in the ImageJ log

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
│   │   │       ├── BrimFileOpener.java       # Main import/open workflow
│   │   │       ├── BrimDropHandler.java      # BRIM-specific drag-and-drop handling
│   │   │       ├── FileInput.java            # Path normalization and file selection helpers
│   │   │       └── PyUtils.java              # Appose environment and Python task helpers
│   │   └── resources/
│   │       ├── plugins.config                # Plugin registration
│   │       └── script/load_brimfile.py       # Python bridge script for data loading
│   └── test/
│       └── java/                             # Unit + integration tests
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
4. Transfer image data using Appose shared-memory `NDArray` objects
5. Convert arrays into calibrated ImageJ hyperstacks (C, Z, T)

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
