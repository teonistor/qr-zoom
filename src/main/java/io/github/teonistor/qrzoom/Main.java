package io.github.teonistor.qrzoom;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.yaml.snakeyaml.Yaml;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toList;

public class Main {

    // Thanks to https://stackoverflow.com/a/18254944/11244682
    static final Transferable NIL_TRANSFERABLE = new Transferable() {
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[0];
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return false;
        }

        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            throw new UnsupportedFlavorException(flavor);
        }
    };

    static final Pattern URL_INPUT = Pattern.compile("(?:https://|http://|)(.+\\.)?zoom\\.us/(?:my|j)/(.+)");

    static final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();


    public static void main(String[] arg) {
        final List<ConfigEntry> entries = new Yaml().loadAs(ClassLoader.getSystemResourceAsStream("config.yml"), Config.class).entries;
        clipboard.setContents(NIL_TRANSFERABLE, null);
        launchScreenshotTool(null, entries.iterator());
    }

    static void launchScreenshotTool(final Throwable prevCause, final Iterator<ConfigEntry> entries) {
        if (!entries.hasNext()) {
            prevCause.printStackTrace();
            return;
        }

        try {
            final ConfigEntry entry = entries.next();
            waitForToolAndDecode(new ProcessBuilder(entry.tool).start(), entry);
        } catch (final IOException e) {
            if (prevCause != null && prevCause != e) {
                e.addSuppressed(prevCause);
            }
            launchScreenshotTool(e, entries);
        }
    }

    static void waitForToolAndDecode(final Process process, final ConfigEntry entry) {
        try {
            process.waitFor();

            final Transferable transferable = clipboard.getContents(null);
            clipboard.setContents(NIL_TRANSFERABLE, null);

            if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                final Object transferData1 = transferable.getTransferData(DataFlavor.imageFlavor);

                if (transferData1 instanceof BufferedImage) {

                    // Thanks https://www.callicoder.com/qr-code-reader-scanner-in-java-using-zxing/
                    final BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource((BufferedImage) transferData1)));
                    final String text = new MultiFormatReader().decode(bitmap).getText();

                    final Matcher match = URL_INPUT.matcher(text);
                    if (match.find()) {
                        // This is terrible
                        final List<String> launchCommand = entry.command.stream().map(part -> URL_INPUT.matcher(match.group()).replaceAll(part)).collect(toList());
                        System.out.println("Read: " + text + ". Launching: " + launchCommand);
                        new ProcessBuilder(launchCommand).start();

                    } else {
                        System.err.println("Didn't understand the text: " + text);
                    }
                } else {
                    System.err.println("Could not cast to BufferedImage");
                }
            } else {
                System.err.println("No image data");
            }
        } catch (final IOException|UnsupportedFlavorException|InterruptedException|NotFoundException e) {
            e.printStackTrace();
        }
    }

    static class Config {
        public List<ConfigEntry> entries;
    }

    static class ConfigEntry {
        public List<String> tool, command;
    }
}
