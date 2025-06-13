import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.border.Border;

/**
 * Przeciągnij i upuść pliki na komponent Swing.
 * Użycie: new FileDrop( myComponent, listener );
 */
public class FileDrop {
    private transient Border oldBorder; // Używamy transient, aby nie było serializowane

    public FileDrop(final java.awt.Component c, final Listener listener) {
        // Tworzymy docelowy obiekt, na który będzie można upuszczać dane
        new DropTarget(c, new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetDragEvent evt) {
                // Wskazujemy, że chcemy zaakceptować upuszczone pliki
                if (evt.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    evt.acceptDrag(DnDConstants.ACTION_COPY);
                    if (listener != null) listener.dragEnter();
                } else {
                    evt.rejectDrag();
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent evt) {
                // Pozwalamy na przeciąganie nad komponentem
            }

            @Override
            public void drop(DropTargetDropEvent evt) {
                try {
                    // Akceptujemy upuszczenie
                    evt.acceptDrop(DnDConstants.ACTION_COPY);

                    // Pobieramy upuszczone dane
                    @SuppressWarnings("unchecked")
                    List<File> droppedFiles = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

                    // Wywołujemy listener
                    if (listener != null) {
                        listener.filesDropped(droppedFiles.toArray(new File[0]));
                    }

                } catch (IOException | UnsupportedFlavorException ex) {
                    System.err.println("FileDrop: " + ex.getMessage());
                } finally {
                    evt.dropComplete(true); // Zakończ operację upuszczania
                    if (listener != null) listener.dragExit(); // Zawsze przywracamy styl po upuszczeniu
                }
            }

            @Override
            public void dragExit(DropTargetEvent evt) {
                if (listener != null) listener.dragExit();
            }

            @Override
            public void dropActionChanged(DropTargetDragEvent evt) {
                // Ignorujemy
            }
        });
    }

    /**
     * Interfejs do obsługi zdarzeń przeciągania i upuszczania plików.
     */
    public interface Listener {
        void filesDropped(File[] files); // Wywoływana, gdy pliki zostaną upuszczone
        void dragEnter(); // Wywoływana, gdy pliki wejdą w obszar komponentu
        void dragExit(); // Wywoływana, gdy pliki opuszczą obszar komponentu
    }
}