"""
Python script for loading BRIM (Brillouin Imaging) files.

This script is loaded as a resource template by BrimFileOpener.java.
String formatting parameters:
    - %%s (first): The file path to the BRIM file (e.g., '/path/to/file.brim.zarr')
    - %%s (second): A Python list of selected quantity names as string literals
                 (e.g., "['Shift', 'Linewidth', 'Amplitude']")
"""

import appose
# brimfile and numpy are already imported in the Python environment by BrimFileOpener.java, so we don't need to import them here.
# import brimfile as brim
# import numpy as np

# Open the BRIM file
f = brim.File('%s')
selected_quantities = %s

# Define outputs in case loading fails
task.outputs['image_shape'] = [0, 0, 0] 
task.outputs['image_data'] = None # NDArray (shared memory to be passed to Java) or None if loading fails
task.outputs['channel_names'] = []
task.outputs['num_channels'] = 0
task.outputs['num_timepoints'] = 0
task.outputs['error_list'] = []

try:
    # Get the list of data groups
    dgs = f.list_data_groups()

    # Get selected channel images
    Quantity = brim.Data.AnalysisResults.Quantity
    PeakType = brim.Data.AnalysisResults.PeakType
    images = [] # a list of lists to hold timepoints (each of the inner lists will hold the channels for that timepoint)
    error_list = []
    image_shape = None
    px_size_ref = None

    for dg in dgs:
        channel_images = []

        d = f.get_data(dg['index'])
        ar = d.get_analysis_results()

        for quantity_name in selected_quantities:
            try:
                quantity_enum = getattr(Quantity, quantity_name)
            except AttributeError:
                error_list.append(f'Unknown quantity: {quantity_name}')
                continue

            try:
                img, px_size = ar.get_image(quantity_enum, PeakType.average)
            except Exception as e:
                error_list.append(f'Failed to load {quantity_name}: {e}')
                continue

            if img is None:
                error_list.append(f'No image data for {quantity_name}')
                continue

            if image_shape is None:
                image_shape = img.shape
                px_size_ref = px_size
            elif img.shape != image_shape:
                error_list.append(f'Incompatible shape for {quantity_name}: {img.shape} != {image_shape}')
                continue

            channel_images.append(img)
        
        images.append(channel_images)

    if image_shape is not None and images: # if we have at least one valid image
        nz, ny, nx = image_shape
        stack_data = appose.NDArray('float32', [len(images), nz, len(images[0]), ny, nx])
        stack_view = stack_data.ndarray()
        for t, timepoint_images in enumerate(images):
            for c, channel_img in enumerate(timepoint_images):
                stack_view[t, :, c, :, :] = channel_img.astype(np.float32, copy=False)
    else:
        stack_data = None
        image_shape = (0, 0, 0)


    task.outputs['image_shape'] = list(image_shape)
    task.outputs['image_data'] = stack_data
    task.outputs['channel_names'] = [quantity_name for quantity_name in selected_quantities]
    task.outputs['num_channels'] = len(selected_quantities)
    task.outputs['num_timepoints'] = len(images)
    task.outputs['error_list'] = error_list

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
