"""
Python script for loading BRIM (Brillouin Imaging) files.

This script is loaded as a resource template by BrimFileOpener.java.
String formatting parameters:
    - %%s (first): The file path to the BRIM file (e.g., '/path/to/file.brim.zarr')
    - %%s (second): A Python list of selected quantity names as string literals
                 (e.g., "['Shift', 'Linewidth', 'Amplitude']")
"""

import appose
import brimfile as brim
import numpy as np

# Open the BRIM file
f = brim.File('%s')
selected_quantities = %s

# Define outputs in case loading fails
task.outputs['image_shape'] = [0, 0, 0]
task.outputs['image_data'] = None
task.outputs['channel_names'] = []
task.outputs['num_channels'] = 0
task.outputs['channel_errors'] = []

try:
    # Get the first data group
    d = f.get_data()

    # Get the first analysis results
    ar = d.get_analysis_results()

    # Get selected channel images
    Quantity = brim.Data.AnalysisResults.Quantity
    PeakType = brim.Data.AnalysisResults.PeakType
    channel_images = []
    channel_names = []
    channel_errors = []
    image_shape = None
    px_size_ref = None

    for quantity_name in selected_quantities:
        try:
            quantity_enum = getattr(Quantity, quantity_name)
        except AttributeError:
            channel_errors.append(f'Unknown quantity: {quantity_name}')
            continue

        try:
            img, px_size = ar.get_image(quantity_enum, PeakType.average)
        except Exception as e:
            channel_errors.append(f'Failed to load {quantity_name}: {e}')
            continue

        if img is None:
            channel_errors.append(f'No image data for {quantity_name}')
            continue

        if image_shape is None:
            image_shape = img.shape
            px_size_ref = px_size
        elif img.shape != image_shape:
            channel_errors.append(f'Incompatible shape for {quantity_name}: {img.shape} != {image_shape}')
            continue

        channel_names.append(quantity_name)
        channel_images.append(img)

    if image_shape is not None and channel_images:
        nz, ny, nx = image_shape
        stack_data = appose.NDArray('float32', [nz, len(channel_images), ny, nx])
        stack_view = stack_data.ndarray()
        for c, channel_img in enumerate(channel_images):
            stack_view[:, c, :, :] = channel_img.astype(np.float32, copy=False)
    else:
        stack_data = None
        image_shape = (0, 0, 0)

    data_group_name = d.get_name()
    ar_name = ar.get_name()

    task.outputs['image_shape'] = list(image_shape)
    task.outputs['image_data'] = stack_data
    task.outputs['data_group_name'] = data_group_name
    task.outputs['ar_name'] = ar_name
    task.outputs['channel_names'] = channel_names
    task.outputs['num_channels'] = len(channel_names)
    task.outputs['channel_errors'] = channel_errors

    # Parse pixel size
    if px_size_ref is not None:
        task.outputs['pixel_depth'] = float(px_size_ref[0].value)
        task.outputs['pixel_height'] = float(px_size_ref[1].value)
        task.outputs['pixel_width'] = float(px_size_ref[2].value)
        task.outputs['pixel_unit'] = str(px_size_ref[2].units)
    else:
        task.outputs['pixel_depth'] = 1.0
        task.outputs['pixel_height'] = 1.0
        task.outputs['pixel_width'] = 1.0
        task.outputs['pixel_unit'] = 'um'
finally:
    f.close()
