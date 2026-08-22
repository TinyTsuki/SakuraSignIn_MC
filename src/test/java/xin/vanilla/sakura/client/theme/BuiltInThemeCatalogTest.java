package xin.vanilla.sakura.client.theme;

import org.junit.Test;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class BuiltInThemeCatalogTest {

    @Test
    public void loadsEveryPackagedThemeByStableId() throws Exception {
        Map<String, int[]> dimensions = new HashMap<>();
        dimensions.put("original", new int[]{500, 1000});
        dimensions.put("sakura", new int[]{500, 1000});
        dimensions.put("clover", new int[]{500, 1000});
        dimensions.put("maple", new int[]{500, 1000});
        dimensions.put("chaos", new int[]{600, 1044});

        assertEquals(Arrays.asList("original", "sakura", "clover", "maple", "chaos"),
                BuiltInThemeCatalog.themeIds());
        for (String id : BuiltInThemeCatalog.themeIds()) {
            BuiltInThemeDescriptor descriptor = BuiltInThemeCatalog.load(id);
            TextureCoordinate coordinates = descriptor.getCoordinates();
            assertEquals(id, descriptor.getId());
            assertEquals("sakura_sign_in:textures/gui/sign_in_calendar_" + id + ".png",
                    descriptor.getTexture());
            assertEquals(dimensions.get(id)[0], coordinates.getTotalWidth());
            assertEquals(dimensions.get(id)[1], coordinates.getTotalHeight());
            assertEquals(!"chaos".equals(id), coordinates.isSpecial());

            BufferedImage image = readTexture(descriptor);
            assertEquals(coordinates.getTotalWidth(), image.getWidth());
            assertEquals(coordinates.getTotalHeight(), image.getHeight());
        }
    }

    @Test
    public void fallsBackToSakuraAndReturnsFreshCoordinates() {
        assertEquals("sakura", BuiltInThemeCatalog.load("../external.png").getId());

        BuiltInThemeDescriptor first = BuiltInThemeCatalog.load("original");
        BuiltInThemeDescriptor second = BuiltInThemeCatalog.load("original");
        assertNotSame(first.getCoordinates(), second.getCoordinates());
        first.getCoordinates().getNotSignedInUV().setX(320);
        assertEquals(0.0, second.getCoordinates().getNotSignedInUV().getX(), 0.0);
    }

    @Test
    public void texturesNoLongerContainSerializedPrivateChunks() throws Exception {
        for (String id : BuiltInThemeCatalog.themeIds()) {
            BuiltInThemeDescriptor descriptor = BuiltInThemeCatalog.load(id);
            String path = "assets/" + descriptor.textureLocation().getNamespace() + "/"
                    + descriptor.textureLocation().getPath();
            try (InputStream input = resource(path)) {
                DataInputStream data = new DataInputStream(input);
                byte[] signature = new byte[8];
                data.readFully(signature);
                boolean reachedEnd = false;
                while (!reachedEnd) {
                    int length = data.readInt();
                    byte[] typeBytes = new byte[4];
                    data.readFully(typeBytes);
                    String type = new String(typeBytes, "US-ASCII");
                    assertFalse("Legacy vacb chunk remains in " + id, "vacb".equals(type));
                    skipFully(data, length + 4);
                    reachedEnd = "IEND".equals(type);
                }
                assertTrue("PNG is missing IEND: " + id, reachedEnd);
            }
        }
    }


    private static BufferedImage readTexture(BuiltInThemeDescriptor descriptor) throws IOException {
        String path = "assets/" + descriptor.textureLocation().getNamespace() + "/"
                + descriptor.textureLocation().getPath();
        try (InputStream input = resource(path)) {
            return ImageIO.read(input);
        }
    }

    private static InputStream resource(String path) throws IOException {
        InputStream input = BuiltInThemeCatalogTest.class.getClassLoader().getResourceAsStream(path);
        if (input == null) {
            throw new IOException("Missing test resource: " + path);
        }
        return input;
    }

    private static void skipFully(DataInputStream input, int bytes) throws IOException {
        int remaining = bytes;
        while (remaining > 0) {
            int skipped = input.skipBytes(remaining);
            if (skipped <= 0) {
                throw new IOException("Unexpected PNG end");
            }
            remaining -= skipped;
        }
    }
}
