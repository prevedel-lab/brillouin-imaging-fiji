# Implementation Summary: BRIM File Plugin for ImageJ/FIJI

## Overview
This document summarizes the current implementation of the BRIM plugin for ImageJ/FIJI.

The plugin opens `.brim.zarr` and `.brim.zip` files through Python `brimfile`, via Appose/Apposed, then converts loaded data into a calibrated ImageJ hyperstack.

## Current Capabilities

1. Open BRIM files from **File > Import > BRIM File (.brim.zarr, .brim.zip)...**.
2. Select one or more quantities (channels) in a dialog before loading.
3. Enumerate BRIM data groups and stack compatible groups as timepoints.
4. Display metadata in a dedicated ImageJ text window.
5. Preserve voxel calibration (`pixelWidth`, `pixelHeight`, `pixelDepth`, unit).
6. Optionally resample XY for square display pixels (enabled by default).
7. Handle BRIM drag-and-drop when the custom drop handler is enabled.

## Main Components

- **BrimFileOpener**: Main plugin workflow (file prompt, quantity selection, Python execution, hyperstack creation).
- **BrimDropHandler**: Custom drag-and-drop handler for `.brim.zip` files and `.brim.zarr` folders.
- **FileInput**: Path normalization, extension validation, and file/folder chooser helpers.
- **PyUtils**: Python environment provisioning and task execution helpers.
- **load_brimfile.py**: Resource script used by Java to load selected quantities from BRIM data groups.

## Java-Python Bridge

### Environment Management
- Uses `Appose.uv()` with a pinned Python version (`3.11`).
- Environment location: `~/.local/share/appose/envs/brimfile`.
- Installs `brimfile` and `appose` automatically when needed.

### Python Tasks
The plugin runs two Python task phases:

1. **Metadata and quantity probe**:
- Opens the file.
- Reads available quantities from the first data group's analysis results.
- Exports metadata and pretty-printed metadata.

2. **Image loading (`load_brimfile.py`)**:
- Iterates data groups.
- Loads selected quantities for each group (first analysis results in that group).
- Builds a time stack from compatible groups.
- Returns shape, calibration values, channel names, and warnings/errors.

### Data Transfer Format
- Image data is transferred through Appose shared memory using `appose.NDArray`.
- Expected C-order tensor shape: `[t, z, c, y, x]`.
- Java validates shape and dtype (`FLOAT32`) before decoding.

## ImageJ Conversion Details

- Hyperstack dimensions are set as `(C=nc, Z=nz, T=nt)`.
- Slice order follows ImageJ stack indexing conventions.
- Multi-channel outputs are wrapped as `CompositeImage` in grayscale mode.
- Display range is initialized from image statistics.
- Default LUT command: `mpl-inferno`.

## Build and Runtime Requirements

- Java: 8+
- Maven: 3.6+
- ImageJ core dependency: `ij 1.54f`
- Appose dependency: `org.apposed:appose:0.11.0`

The Maven compiler is configured with `release=8` for FIJI stable compatibility.

## Testing

### Unit Tests
Unit tests cover file/path handling and `BrimFileOpener` internals, including:

- Python list literal escaping
- Resource script loading
- NDArray shared-memory decoding (5D and legacy 4D)
- Hyperstack conversion and calibration
- XY resampling behavior

Run unit tests:

```bash
mvn clean test
```

### Integration Tests
Integration tests are in `PyUtilsIT` and are gated behind a Maven profile.

Run integration tests:

```bash
mvn -Pintegration-tests verify
```

## Known Limitations

1. Quantity list is currently derived from the first data group.
2. The first analysis result is used for each data group.
3. Data groups with incompatible shapes are skipped.
4. There is no UI yet for manually selecting data-group subsets or analysis-result variants.

## References

- [Apposed](https://apposed.org/)
- [brimfile package](https://github.com/prevedel-lab/brimfile)
- [BRIM format specification](https://github.com/prevedel-lab/Brillouin-standard-file)
