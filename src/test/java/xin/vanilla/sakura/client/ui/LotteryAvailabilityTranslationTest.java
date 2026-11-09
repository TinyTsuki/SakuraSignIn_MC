package xin.vanilla.sakura.client.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LotteryAvailabilityTranslationTest {
    @Test
    public void chineseUnavailableMessageDescribesDrawing() throws Exception {
        assertMessage("zh_cn", "\u8be5\u5956\u6c60\u6682\u65f6\u65e0\u6cd5\u62bd\u53d6");
    }

    @Test
    public void englishUnavailableMessageDescribesDrawing() throws Exception {
        assertMessage("en_us", "Draws are unavailable for this pool");
    }

    private void assertMessage(String language, String expected) throws Exception {
        String path = "/assets/sakura_sign_in/lang/" + language + ".json";
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(path, stream);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject translations = new JsonParser().parse(reader).getAsJsonObject();
                assertEquals(expected, translations.get("word.sakura_sign_in.lottery_payment_unavailable").getAsString());
            }
        }
    }
}
