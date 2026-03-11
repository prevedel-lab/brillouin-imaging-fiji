package net.imagej.brimfile;

import ij.IJ;
import ij.ImageJ;
import ij.gui.Toolbar;
import ij.plugin.DragAndDrop;
import ij.plugin.PlugIn;

import java.awt.EventQueue;
import java.awt.Panel;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.File;
import java.net.URI;
import java.net.URLDecoder;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Installs a custom drag-and-drop handler so BRIM files and folders open with this plugin.
 */
public class BrimDropHandler implements PlugIn, DropTargetListener {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private final DragAndDrop fallback = new DragAndDrop();

    static void installIfNeeded() {
        if (!INSTALLED.get()) {
            IJ.runPlugIn(BrimDropHandler.class.getName(), "");
        }
    }

    @Override
    public void run(String arg) {
        if (INSTALLED.getAndSet(true)) {
            return;
        }

        ImageJ imageJ = IJ.getInstance();
        if (imageJ == null) {
            INSTALLED.set(false);
            return;
        }

        imageJ.setDropTarget(null);
        new DropTarget(imageJ, this);

        Toolbar toolbar = Toolbar.getInstance();
        if (toolbar != null) {
            new DropTarget(toolbar, this);
        }

        Panel statusBar = imageJ.getStatusBar();
        if (statusBar != null) {
            new DropTarget(statusBar, this);
        }

        IJ.log("BRIM File Opener: custom drag-and-drop handler enabled.");
    }

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
        dtde.acceptDrag(DnDConstants.ACTION_COPY);
    }

    @Override
    public void dragOver(DropTargetDragEvent dtde) {
        // No-op
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent dtde) {
        // No-op
    }

    @Override
    public void dragExit(DropTargetEvent dte) {
        // No-op
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
        boolean handledAny = false;
        try {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
            Transferable transferable = dtde.getTransferable();

            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                Object transferData = transferable.getTransferData(DataFlavor.javaFileListFlavor);
                if (transferData instanceof List<?>) {
                    List<?> files = (List<?>) transferData;
                    for (Object item : files) {
                        if (item instanceof File) {
                            handleDroppedFile((File) item);
                            handledAny = true;
                        }
                    }
                }
            } else if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                Object transferData = transferable.getTransferData(DataFlavor.stringFlavor);
                if (transferData != null) {
                    handledAny = handleDroppedText(transferData.toString());
                }
            }
        } catch (Exception e) {
            IJ.handleException(e);
        } finally {
            dtde.dropComplete(handledAny);
        }
    }

    private boolean handleDroppedText(String text) {
        boolean handledAny = false;
        String[] lines = text.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            File file = fileFromDropEntry(line);
            if (file != null) {
                handleDroppedFile(file);
                handledAny = true;
                continue;
            }

            if (line.startsWith("http://") || line.startsWith("https://")) {
                IJ.open(line);
                handledAny = true;
            }
        }
        return handledAny;
    }

    private File fileFromDropEntry(String entry) {
        try {
            if (entry.startsWith("file:/")) {
                File fileFromUri = new File(new URI(entry));
                return fileFromUri.exists() ? fileFromUri : null;
            }

            String decoded = URLDecoder.decode(entry.replace("+", "%2b"), "UTF-8");
            File decodedFile = new File(decoded);
            return decodedFile.exists() ? decodedFile : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void handleDroppedFile(File file) {
        if (file == null) {
            return;
        }

        String path = FileInput.normalizePath(file.getAbsolutePath());
        if (FileInput.isBrimFile(path)) {
            EventQueue.invokeLater(() -> BrimFileOpener.openAndShow(path));
            return;
        }

        fallback.openFile(file);
    }
}
