package io.github.teonistor.qrzoom;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    static final String[][] SCREENSHOT_TOOLS = {
            {"gnome-screenshot", "-iac"},
            {"snippingtool.exe"}};

    static final Pattern URL_INPUT = Pattern.compile("(?:https://|http://|)(.+\\.)?zoom\\.us/(?:my|j)/(.+)");

    static final String[][] LAUNCH_COMMANDS = {
            // These correspond to the tools above, in order
            {"xdg-open", "zoommtg://$1zoom.us/join?action=join&confno=$2"},
            {"start",    "zoommtg://$1zoom.us/join?action=join&confno=$2"}};


    public static void main(String[] arg) {
        final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(NIL_TRANSFERABLE, null);

        launchScreenshotTool(0, null, clipboard);
    }

    static void launchScreenshotTool(final int index, final Throwable prevCause, final Clipboard clipboard) {
        if (index >= SCREENSHOT_TOOLS.length) {
            prevCause.printStackTrace();
        }

        try {
            waitForToolAndDecode(index, new ProcessBuilder(SCREENSHOT_TOOLS[index]).start(), clipboard);
        } catch (final IOException e) {
            if (prevCause != null && prevCause != e) {
                e.addSuppressed(prevCause);
            }
            launchScreenshotTool(index + 1, e, clipboard);
        }
    }

    static void waitForToolAndDecode(final int index, final Process process, final Clipboard clipboard) {
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
                        final String[] launchCommand = LAUNCH_COMMANDS[index];
                        Arrays.setAll(launchCommand, i -> URL_INPUT.matcher(match.group()).replaceAll(launchCommand[i]));
                        System.out.println("Read: " + text + ". Launching: " + Arrays.toString(launchCommand));
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
}
