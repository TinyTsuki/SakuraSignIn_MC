package xin.vanilla.sakura;

import org.junit.Test;
import xin.vanilla.sakura.screen.component.KeyEventManager;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import xin.vanilla.sakura.util.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;

public class MyTest {

    @Test
    public void dateTest() {
        System.out.println(DateUtils.calculateContinuousDays(new ArrayList<>() {{
            add(DateUtils.getDate(2024, 10, 31));
            add(DateUtils.getDate(2024, 10, 30));
            add(DateUtils.getDate(2024, 10, 29));
            add(DateUtils.getDate(2024, 10, 28));
            add(DateUtils.getDate(2024, 10, 20));
            add(DateUtils.getDate(2024, 10, 19));
            add(DateUtils.getDate(2024, 10, 18));
            add(DateUtils.getDate(2024, 10, 17));
        }}, new Date()));
    }

    private static final String themeName = "clover";

    private static void testWriteZTxt(String fileName) {
        try {
            File sourceFile;
            File targetFile;
            if (StringUtils.isNullOrEmpty(fileName)) {
                fileName = themeName;
            }
            sourceFile = new File("F:\\Design\\PS\\樱花签\\sign_in_calendar_" + fileName + "_source.png");
            targetFile = new File("src/main/resources/assets/sakura_sign_in/textures/gui/sign_in_calendar_" + fileName + ".png");
            PNGUtils.writeZTxt(sourceFile, targetFile, new LinkedHashMap<>() {{
                put("titleStartX", "20");
                put("titleStartY", "20");
                put("titleWidth", "50");
                put("titleHeight", "20");
                put("subTitleStartX", "20");
                put("subTitleStartY", "20");
                put("subTitleWidth", "50");
                put("subTitleHeight", "20");
                put("cellStartX", "20");
                put("cellStartY", "40");
                put("cellWidth", "14");
                put("cellHeight", "14");
                put("cellHMargin", "12");
                put("cellVMargin", "8");
                put("leftButtonStartX", "20");
                put("leftButtonStartY", "20");
                put("leftButtonWidth", "20");
                put("leftButtonHeight", "20");
                put("rightButtonStartX", "20");
                put("rightButtonStartY", "20");
                put("rightButtonWidth", "20");
                put("rightButtonHeight", "20");
                put("totalWidth", "20");
                put("totalHeight", "20");
                put("Software", "Minecraft SakuraSignIn");
            }});
            System.out.println(PNGUtils.readAllZTxt(targetFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static void testWriteChunk(String fileName) throws IOException, ClassNotFoundException {
        File sourceFile;
        File targetFile;
        if (StringUtils.isNullOrEmpty(fileName)) {
            fileName = themeName;
        }
        sourceFile = new File("F:\\Design\\PS\\樱花签\\sign_in_calendar_" + fileName + "_source.png");
        targetFile = new File("src/main/resources/assets/sakura_sign_in/textures/gui/sign_in_calendar_" + fileName + ".png");
        File tempFile = new File("src/main/resources/assets/sakura_sign_in/textures/gui/checkin_background_temp.png");
        TextureCoordinate aDefault = TextureCoordinate.getDefault();
        switch (fileName) {
            case "original":
                aDefault.setSpecial(true);
                // aDefault.setTotalHeight(880);
                break;
            case "sakura":
                aDefault.setSpecial(true);
                aDefault.getCellCoordinate().setX(70).setY(128).setWidth(34).setHeight(34);
                aDefault.setCellHMargin(20);
                aDefault.setCellVMargin(24);
                aDefault.setTextColorDefault(0xFFFFD9E2);
                aDefault.setTextColorCurrent(0xFFF27690);
                aDefault.setTextColorCanRepair(0xFFFFFFBB);
                aDefault.setTextColorToday(0xFFE03D63);
                aDefault.setTextColorDate(0xFF000000);
                aDefault.setSignInInfoCoordinate(new Coordinate().setX(375).setY(5).setWidth(120).setHeight(96));
                aDefault.setSignInInfoUV(new Coordinate().setU0(200).setV0(640).setUWidth(60).setVHeight(48));
                break;
            case "clover":
                aDefault.setSpecial(true);
                // aDefault.setTotalHeight(880);
                aDefault.getCellCoordinate().setX(80);
                aDefault.setTextColorDefault(0xFFEFCE8B);
                aDefault.setTextColorCurrent(0xFF426812);
                aDefault.setTextColorCanRepair(0xFFEFCE8B);
                aDefault.setTextColorToday(0xFFB2D87C);
                aDefault.setTextColorDate(0xFF000000);
                aDefault.setSignInInfoCoordinate(new Coordinate().setX(400).setY(2).setWidth(80).setHeight(75));
                aDefault.setSignInInfoUV(new Coordinate().setU0(200).setV0(640).setUWidth(32).setVHeight(30));
                break;
            case "maple":
                aDefault.setSpecial(true);
                // aDefault.setTotalHeight(880);
                aDefault.setTextColorDefault(0xFFFFAA85);
                aDefault.setTextColorCurrent(0xFFCE4906);
                aDefault.setTextColorCanRepair(0xFFFFFCD2);
                aDefault.setTextColorToday(0xFFFF4E00);
                aDefault.setTextColorDate(0xFF000000);
                aDefault.setSignInInfoCoordinate(new Coordinate().setX(400).setY(2).setWidth(80).setHeight(75));
                aDefault.setSignInInfoUV(new Coordinate().setU0(200).setV0(640).setUWidth(32).setVHeight(30));
                break;
            case "chaos":
                aDefault.setWeekStart(1);
                aDefault.setTextColorDefault(0xFFFFD9E2);
                aDefault.setTextColorCurrent(0xFFF27690);
                aDefault.setTextColorCanRepair(0xFFFFFFBB);
                aDefault.setTextColorToday(0xFFE03D63);
                aDefault.setTextColorDate(0xFF000000);
                aDefault.setTotalWidth(600);
                aDefault.setTotalHeight(1044);
                aDefault.getYearCoordinate().setX(168).setY(60).setWidth(92).setHeight(16);
                aDefault.getMonthCoordinate().setX(410).setY(60).setWidth(62).setHeight(16);
                aDefault.getCellCoordinate().setX(91).setY(116).setWidth(34).setHeight(34);
                aDefault.setCellHMargin(30);
                aDefault.setCellVMargin(8);
                aDefault.setDateOffset(24);
                aDefault.getUpArrowCoordinate().setX(168).setY(60).setWidth(92).setHeight(16);
                aDefault.getDownArrowCoordinate().setX(168).setY(60).setWidth(92).setHeight(16);
                aDefault.getLeftArrowCoordinate().setX(410).setY(60).setWidth(62).setHeight(16);
                aDefault.getRightArrowCoordinate().setX(410).setY(60).setWidth(62).setHeight(16);
                aDefault.getThemeCoordinate().setX(356).setY(440).setWidth(40).setHeight(48);
                aDefault.getBgUV().setU0(0).setV0(0).setUWidth(600).setVHeight(500);
                aDefault.getTooltipUV().setU0(0).setV0(828).setUWidth(300).setVHeight(96);
                aDefault.getArrowUV().setU0(0).setV0(500).setUWidth(40).setVHeight(40);
                aDefault.getArrowHoverUV().setU0(0).setV0(500).setUWidth(40).setVHeight(40);
                aDefault.getArrowTapUV().setU0(40).setV0(500).setUWidth(40).setVHeight(40);
                aDefault.getNotSignedInUV().setU0(80).setV0(500).setUWidth(40).setVHeight(40);
                aDefault.getSignedInUV().setU0(120).setV0(500).setUWidth(40).setVHeight(40);
                aDefault.getRewardedUV().setU0(160).setV0(500).setUWidth(40).setVHeight(40);
                aDefault.getBuffUV().setU0(200).setV0(500).setUWidth(40).setVHeight(40);
                aDefault.getPointUV().setU0(240).setV0(500).setUWidth(40).setVHeight(40);
                aDefault.getLevelUV().setU0(280).setV0(500).setUWidth(40).setVHeight(40);
                aDefault.getCardUV().setU0(320).setV0(500).setUWidth(40).setVHeight(40);
                aDefault.getMessageUV().setU0(360).setV0(500).setUWidth(40).setVHeight(40);
                aDefault.getThemeUV().setU0(0).setV0(540).setUWidth(80).setVHeight(96);
                aDefault.getThemeHoverUV().setU0(0).setV0(636).setUWidth(80).setVHeight(96);
                aDefault.getThemeTapUV().setU0(0).setV0(732).setUWidth(80).setVHeight(96);
                aDefault.getOptionBgUV().setU0(0).setV0(924).setUWidth(120).setVHeight(120);
                aDefault.getHelpUV().setU0(120).setV0(924).setUWidth(40).setVHeight(40);
                aDefault.getDownloadUV().setU0(160).setV0(924).setUWidth(40).setVHeight(40);
                aDefault.getUploadUV().setU0(200).setV0(924).setUWidth(40).setVHeight(40);
                aDefault.getFolderUV().setU0(240).setV0(924).setUWidth(40).setVHeight(40);
                aDefault.getSortUV().setU0(280).setV0(924).setUWidth(40).setVHeight(40);
                aDefault.getSignInBtnUV().setU0(120).setV0(964);
                aDefault.getRewardOptionBtnUV().setU0(180).setV0(964);
                aDefault.setSignInInfoCoordinate(new Coordinate().setX(485).setY(0).setWidth(120).setHeight(150));
                aDefault.setSignInInfoUV(new Coordinate().setU0(400).setV0(500).setUWidth(120).setVHeight(150));
                break;
        }
        PNGUtils.writePrivateChunk(sourceFile, tempFile, "vacb", aDefault, true);
        PNGUtils.writeZTxtByKey(tempFile, targetFile, "Software", "Minecraft SakuraSignIn");
        tempFile.deleteOnExit();
        TextureCoordinate backgroundConf = PNGUtils.readLastPrivateChunk(targetFile, "vacb");
        System.out.println(backgroundConf);
    }

    @Test
    public void pngTest() throws IOException, ClassNotFoundException {
        // testWriteZTxt("original");
        testWriteChunk("original");
        testWriteChunk("sakura");
        testWriteChunk("clover");
        testWriteChunk("maple");
        testWriteChunk("chaos");
    }

    @Test
    public void keyTest() {
        KeyEventManager manager = new KeyEventManager();
        manager.keyPressed(GLFWKey.GLFW_KEY_A);
        manager.keyPressed(GLFWKey.GLFW_KEY_LEFT_SHIFT);
        manager.keyPressed(GLFWKey.GLFW_KEY_RIGHT_CONTROL);
        manager.mouseClicked(GLFWKey.GLFW_MOUSE_BUTTON_LEFT, 0, 0);
        manager.mouseClicked(GLFWKey.GLFW_MOUSE_BUTTON_RIGHT, 0, 0);
        {
            String join = GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_A, GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_LEFT_SHIFT);
            boolean keyPressed = manager.isKeyPressed(join);
            System.out.println(join);
            System.out.println(keyPressed);
        }
        {
            String join = GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_A, GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_LEFT_SHIFT);
            boolean keyPressed = manager.isKeyPressed(join);
            System.out.println(join);
            System.out.println(keyPressed);
        }
        {
            String join = GLFWKeyHelper.getMouseDisplayString(GLFWKey.GLFW_MOUSE_BUTTON_LEFT, GLFWKey.GLFW_MOUSE_BUTTON_RIGHT);
            boolean keyPressed = manager.isMousePressed(join);
            System.out.println(join);
            System.out.println(keyPressed);
        }
    }
}
