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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Main {

    // Thanks to https://stackoverflow.com/a/18254944/11244682
    static final Transferable NIL_TRANSFERABLE = new Transferable() {
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[0];
        }

        public boolean isDataFlavorSupported(final DataFlavor flavor) {
            return false;
        }

        public Object getTransferData(final DataFlavor flavor) throws UnsupportedFlavorException {
            throw new UnsupportedFlavorException(flavor);
        }
    };

//    static final Pattern URL_INPUT = Pattern.compile("(?:https://|http://|)(.+\\.)?zoom\\.us/(?:my|j)/(.+)");

    static final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();


    public static void main(final String[] arg) {
        final Config config = new Yaml().loadAs(ClassLoader.getSystemResourceAsStream("config.yml"), Config.class);

        final Pattern webUrl = Pattern.compile(config.webUrlRegex);
        final Iterator<ConfigEntry> entries = config.entries.iterator();

        clipboard.setContents(NIL_TRANSFERABLE, null);
        launchScreenshotTool(null, entries, webUrl, config.nativeUrlFormat);
    }

    static void launchScreenshotTool(final Throwable prevCause, final Iterator<ConfigEntry> entries, final Pattern webUrl, final String nativeUrlFormat) {
        if (!entries.hasNext()) {
            prevCause.printStackTrace();
            return;
        }

        try {
            final ConfigEntry entry = entries.next();
            waitForToolAndDecode(new ProcessBuilder(entry.tool).start(), webUrl, nativeUrlFormat);
        } catch (final IOException e) {
            if (prevCause != null && prevCause != e) {
                e.addSuppressed(prevCause);
            }
            launchScreenshotTool(e, entries, webUrl, nativeUrlFormat);
        }
    }

    static void waitForToolAndDecode(final Process process, final Pattern webUrl, final String nativeUrlFormat) {
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

                    final Matcher match = webUrl.matcher(text);
                    if (match.find()) {
                        final String nativeUrl = webUrl.matcher(match.group()).replaceAll(nativeUrlFormat);
                        System.out.printf("Read '%s'. Launching '%s'%n", text, nativeUrl);
                        Desktop.getDesktop().browse(new URI(nativeUrl));

                    } else {
                        System.err.println("Didn't understand the text: " + text);
                    }
                } else {
                    System.err.println("Could not cast to BufferedImage");
                }
            } else {
                System.err.println("No image data");
            }
        } catch (final IOException|UnsupportedFlavorException|InterruptedException|NotFoundException|URISyntaxException e) {
            e.printStackTrace();
        }
    }

    static class Config {
        public String webUrlRegex;
        public String nativeUrlFormat;
        public List<ConfigEntry> entries;
    }

    static class ConfigEntry {
        public List<String> tool;
    }
}
