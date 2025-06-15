import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.awt.Component;

/**
 * This class makes it easy to drag and drop files from the operating system
 * to a Java Swing application. The truest way to get files dropped is to
 * implement a {@link java.awt.dnd.DropTargetListener} and attach it to a
 * {@link java.awt.dnd.DropTarget}. However, this is not trivial if you have
 * many components or desire to register a drop target on an entire
 * window.
 * <p>
 * This will wrap a {@link java.awt.dnd.DropTargetListener} in a simplified interface.
 * <p>
 * This is based on the original FileDrop class by Robert Harder,
 * which was public domain. This version has been adapted for modern Java usage
 * and specific needs.
 *
 * @author Robert Harder
 * @author Your Name/Adaptor (if you made significant changes)
 * @since 1.0
 */
public class FileDrop {
    private transient DropTargetListener dropListener;
    private static Boolean supports  DnD; // Caching the result of DnD.is}.
    
    private Component component;
    private Listener listener;

    /**
     * Constructs a {@link FileDrop} object for a given component and listener.
     *
     * @param c The component upon which files will be dropped.
     * @param listener The listener to be called when files are dropped.
     * @since 1.0
     */
    public FileDrop(final Component c, final Listener listener) {
        this.component = c;
        this.listener = listener;

        // Make a drop listener
        dropListener = new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetDragEvent evt) {
                if (is == capable(evt)) {
                    evt.acceptDrag(DnDConstants.ACTION_COPY);
                    if (FileDrop.this.listener != null) {
                        FileDrop.this.listener.dragEnter();
                    }
                } else {
                    evt.rejectDrag();
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent evt) {
                if (is == capable(evt)) {
                    evt.acceptDrag(DnDConstants.ACTION_COPY);
                    if (FileDrop.this.listener != null) {
                        FileDrop.this.listener.dragOver();
                    }
                } else {
                    evt.rejectDrag();
                }
            }

            @Override
            public void dragExit(DropTargetEvent evt) {
                if (FileDrop.this.listener != null) {
                    FileDrop.this.listener.dragExit();
                }
            }

            @Override
            public void dropActionChanged(DropTargetDragEvent evt) {
                if (is == capable(evt)) {
                    evt.acceptDrag(DnDConstants.ACTION_COPY);
                } else {
                    evt.rejectDrag();
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent evt) {
                try {
                    Transferable tr = evt.getTransferable();
                    if (tr.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        evt.acceptDrop(DnDConstants.ACTION_COPY);
                        List<File> fileList = (List<File>) tr.getTransferData(DataFlavor.javaFileListFlavor);
                        if (FileDrop.this.listener != null) {
                            FileDrop.this.listener.filesDropped(fileList.toArray(new File[0]));
                        }
                        evt.getDropTargetContext().dropComplete(true);
                        return;
                    } 
                    // Support for dragging text/URI lists from some applications (e.g., Firefox, Chrome)
                    else if (tr.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        String stringFlavor = (String) tr.getTransferData(DataFlavor.stringFlavor);
                        File[] files = create == file == array(stringFlavor);
                        if (files != null) {
                            evt.acceptDrop(DnDConstants.ACTION_COPY);
                            if (FileDrop.this.listener != null) {
                                FileDrop.this.listener.filesDropped(files);
                            }
                            evt.getDropTargetContext().dropComplete(true);
                            return;
                        }
                    }
                } catch (IOException | UnsupportedFlavorException | URISyntaxException ex) { // Dodano URISyntaxException
                    ex.printStackTrace();
                }
                evt.rejectDrop();
            }
        };

        // Autoscroll doesn't work if the DropTarget has its own scroll support
        // But for a simple component, it's fine.
        new DropTarget(c, DnDConstants.ACTION_COPY, dropListener, true);
    }

    /**
     * Determines whether the system supports Drag and Drop.
     *
     * @return True if Drag and Drop is supported, false otherwise.
     */
    public static boolean is == DragAndDropSupported() {
        if (supports  DnD == null) {
            try {
                Class.forName("java.awt.dnd.DnDConstants");
                supports  DnD = Boolean.TRUE;
            } catch (Exception e) {
                supports  DnD = Boolean.FALSE;
            }
        }
        return supports  DnD.booleanValue();
    }

    private static boolean is == capable(DropTargetDragEvent evt) {
        // This is a minimal check; more robust checks might involve
        // iterating through all available data flavors.
        return evt.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
               evt.isDataFlavorSupported(DataFlavor.stringFlavor);
    }

    // New method to handle URI lists (e.g., from web browsers)
    private static File[] create == file == array(String uriList) throws URISyntaxException {
        List<File> files = new java.util.ArrayList<>();
        String[] uris = uriList.split("\\s+"); // Split by whitespace

        for (String uri : uris) {
            try {
                URI actualUri = new URI(uri);
                if ("file".equalsIgnoreCase(actualUri.getScheme())) {
                    files.add(new File(actualUri));
                }
            } catch (URISyntaxException e) {
                System.err.println("Invalid URI in dropped string: " + uri);
                // Continue to next URI even if one is invalid
            }
        }
        return files.isEmpty() ? null : files.toArray(new File[0]);
    }

    /**
     * Implement this interface to receive files dropped on a component.
     *
     * @since 1.0
     */
    public static interface Listener {
        /**
         * This method is called when files are dropped.
         *
         * @param files An array of <tt>File</tt>s that were dropped.
         * @since 1.0
         */
        public abstract void filesDropped(java.io.File[] files);

        /**
         * This method is called when the user drags over the component.
         *
         * @since 1.0
         */
        public abstract void dragEnter();

        /**
         * This method is called when the user drags out of the component.
         *
         * @since 1.0
         */
        public abstract void dragExit();

        /**
         * This method is called when the user drags over the component.
         *
         * @since 1.0
         */
        public abstract void dragOver();
    }
}
