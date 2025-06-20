package xin.vanilla.sakura.util;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

@OnlyIn(Dist.CLIENT)
public class PNGUtils {

    /**
     * 根据关键字读取PNG文件中的zTxt信息
     *
     * @param pngFile       PNG文件
     * @param targetKeyWord zTxt数据中目标关键字
     */
    public static String readZTxtByKey(File pngFile, String targetKeyWord) throws IOException {
        Map<String, String> ztxtMap = readAllZTxt(pngFile);
        return ztxtMap.getOrDefault(targetKeyWord, null);
    }

    /**
     * 读取PNG文件中的所有zTXt块
     *
     * @param pngFile PNG文件
     */
    public static Map<String, String> readAllZTxt(File pngFile) throws IOException {
        Map<String, String> ztxtMap = new LinkedHashMap<>();

        try (FileInputStream fis = new FileInputStream(pngFile);
             DataInputStream dis = new DataInputStream(fis)) {

            byte[] pngHeader = new byte[8];
            dis.readFully(pngHeader);

            if (!isPNGHeaderValid(pngHeader)) {
                throw new IOException("Invalid PNG file.");
            }

            while (dis.available() > 0) {
                int length = dis.readInt();
                byte[] chunkType = new byte[4];
                dis.readFully(chunkType);

                String chunkName = new String(chunkType, StandardCharsets.UTF_8);

                byte[] data = new byte[length];
                dis.readFully(data);
                // 跳过校验和部分
                dis.skipBytes(4);
                // 如果块的名称是"zTXt"
                if (chunkName.equals("zTXt")) {
                    // 读取空字符终止的字符串
                    String keyword = readNullTerminatedString(data);
                    // 计算下一个字段的索引位置
                    int index = keyword.length() + 1;

                    int compressionMethod = data[index];
                    // 将索引指向下一个字段
                    index += 1;
                    // 若压缩方法不是0（表示未压缩），抛出异常
                    if (compressionMethod != 0) {
                        throw new IOException("Unsupported compression method in zTXt block.");
                    }
                    byte[] compressedText = new byte[length - index];
                    System.arraycopy(data, index, compressedText, 0, compressedText.length);
                    // 解压缩文本
                    String decompressedText = inflateText(compressedText);
                    ztxtMap.put(keyword, decompressedText);
                }
            }
        }
        return ztxtMap;
    }

    /**
     * 根据关键字从输入流中读取PNG的zTXt信息
     *
     * @param inputStream   PNG输入流
     * @param targetKeyWord zTXt数据目标关键字
     */
    public static String readZTxtByKey(InputStream inputStream, String targetKeyWord) throws IOException {
        Map<String, String> ztxtMap = readAllZTxt(inputStream);
        return ztxtMap.getOrDefault(targetKeyWord, null);
    }

    /**
     * 读取输入流中的所有zTXt块
     *
     * @param inputStream PNG输入流
     */
    public static Map<String, String> readAllZTxt(InputStream inputStream) throws IOException {
        Map<String, String> ztxtMap = new LinkedHashMap<>();

        try (DataInputStream dis = new DataInputStream(inputStream)) {

            byte[] pngHeader = new byte[8];
            dis.readFully(pngHeader);

            if (!isPNGHeaderValid(pngHeader)) {
                throw new IOException("Invalid PNG stream.");
            }

            while (dis.available() > 0) {
                int length = dis.readInt();
                byte[] chunkType = new byte[4];
                dis.readFully(chunkType);

                String chunkName = new String(chunkType, StandardCharsets.UTF_8);

                byte[] data = new byte[length];
                dis.readFully(data);
                // 跳过校验和部分
                dis.skipBytes(4);
                // 如果块的名称是"zTXt"
                if (chunkName.equals("zTXt")) {
                    String keyword = readNullTerminatedString(data);
                    // 计算下一个字段的索引位置
                    int index = keyword.length() + 1;

                    int compressionMethod = data[index];
                    // 将索引指向下一个字段
                    index += 1;
                    // 若压缩方法不是0（表示未压缩），抛出异常
                    if (compressionMethod != 0) {
                        throw new IOException("Unsupported compression method in zTXt block.");
                    }
                    byte[] compressedText = new byte[length - index];
                    System.arraycopy(data, index, compressedText, 0, compressedText.length);
                    String decompressedText = inflateText(compressedText);
                    ztxtMap.put(keyword, decompressedText);
                }
            }
        }
        return ztxtMap;
    }


    /**
     * 根据关键字更新zTxt标签信息，并写入到新的文件
     *
     * @param pngFile    输入的PNG文件
     * @param outputFile 输出的PNG文件
     * @param keyWord    zTxt数据目标关键字
     * @param text       zTxt数据目标文本内容
     */
    public static void writeZTxtByKey(File pngFile, File outputFile, String keyWord, String text) throws IOException {
        Map<String, String> ztxtMap = readAllZTxt(pngFile);
        ztxtMap.put(keyWord, text);
        writeZTxt(pngFile, outputFile, ztxtMap);
    }

    /**
     * 向PNG文件中添加zTXT chunk
     *
     * @param pngFile    输入的PNG文件
     * @param outputFile 输出的PNG文件
     * @param zTxtData   包含zTXT数据的Map
     */
    public static void writeZTxt(File pngFile, File outputFile, Map<String, String> zTxtData) throws IOException {
        try (FileInputStream fis = new FileInputStream(pngFile);
             FileOutputStream fos = new FileOutputStream(outputFile);
             DataOutputStream dos = new DataOutputStream(fos)) {

            // 读取PNG文件的头部信息
            byte[] pngHeader = new byte[8];
            fis.read(pngHeader);
            dos.write(pngHeader);

            // 遍历PNG文件中的chunk，直到遇到IEND chunk
            while (fis.available() > 0) {
                int length = readInt(fis);
                byte[] chunkType = new byte[4];
                fis.read(chunkType);
                String chunkName = new String(chunkType, StandardCharsets.UTF_8);

                byte[] data = new byte[length];
                fis.read(data);
                int crc = readInt(fis);

                // 在IEND chunk前插入zTXT chunk
                if (chunkName.equals("IEND")) {
                    for (Map.Entry<String, String> entry : zTxtData.entrySet()) {
                        writeZTxtChunk(dos, entry.getKey(), entry.getValue());
                    }
                }

                // 写入当前chunk到输出流
                writeChunk(dos, chunkName, data, crc);
            }
        }
    }

    /**
     * 向PNG文件中写入私有块
     *
     * @param pngFile    输入的PNG文件
     * @param outputFile 输出的PNG文件
     * @param chunkType  块类型
     * @param object     要写入的对象，
     */
    public static void writePrivateChunk(File pngFile, File outputFile, String chunkType, Object object) throws IOException {
        writePrivateChunk(pngFile, outputFile, chunkType, object, false);
    }

    /**
     * 向PNG文件中写入私有块
     *
     * @param pngFile        输入的PNG文件
     * @param outputFile     输出的PNG文件
     * @param chunkType      要写入的块类型
     * @param object         要写入的对象
     * @param deleteExisting 是否删除已存在的相同类型块
     */
    public static void writePrivateChunk(File pngFile, File outputFile, String chunkType, Object object, boolean deleteExisting) throws IOException {
        // 将对象序列化为字节数组
        byte[] data = serializeObject(object);

        try (FileInputStream fis = new FileInputStream(pngFile);
             FileOutputStream fos = new FileOutputStream(outputFile);
             DataOutputStream dos = new DataOutputStream(fos)) {

            // 读取PNG文件的头部信息
            byte[] pngHeader = new byte[8];
            fis.read(pngHeader);
            dos.write(pngHeader);

            // 记录IEND块的相关信息（不直接写入）
            byte[] iendChunkData = null;
            int iendChunkCRC = 0;

            // 遍历PNG文件中的每个块
            while (fis.available() > 0) {
                // 读取块的长度
                int length = readInt(fis);
                // 读取块的类型
                byte[] typeBuffer = new byte[4];
                fis.read(typeBuffer);

                // 读取块的数据
                byte[] chunkData = new byte[length];
                fis.read(chunkData);
                // 读取块的CRC校验值
                int crc = readInt(fis);

                // 将块的类型转换为字符串
                String currentChunkType = new String(typeBuffer, StandardCharsets.UTF_8);

                // 如果遇到IEND块，则暂存，不立即写入
                if (currentChunkType.equals("IEND")) {
                    iendChunkData = chunkData;
                    iendChunkCRC = crc;
                    continue;  // 暂不写入
                }

                // 如果要删除已存在的相同类型块，则跳过该块
                if (deleteExisting && currentChunkType.equals(chunkType)) {
                    continue;
                }

                // 写入当前块
                writeChunk(dos, currentChunkType, chunkData, crc);
            }

            // 在IEND块之前写入新的私有块
            writeChunk(dos, chunkType, data, calculateCRC(chunkType.getBytes(StandardCharsets.UTF_8), data));

            // 最后写入IEND块
            if (iendChunkData != null) {
                writeChunk(dos, "IEND", iendChunkData, iendChunkCRC);
            }
        }
    }

    /**
     * 从PNG文件中读取第一个指定类型的私有chunk
     *
     * @param pngFile   要读取的PNG文件
     * @param chunkType 要读取的chunk类型
     */
    public static <T> T readFirstPrivateChunk(File pngFile, String chunkType) throws IOException, ClassNotFoundException {
        List<T> objects = readPrivateChunk(pngFile, chunkType, true);
        return objects.isEmpty() ? null : objects.get(0);
    }

    /**
     * 读取PNG文件中指定类型的最后一个私有块数据
     *
     * @param pngFile   PNG文件对象
     * @param chunkType 要读取的chunk类型
     */
    public static <T> T readLastPrivateChunk(File pngFile, String chunkType) throws IOException, ClassNotFoundException {
        List<T> objects = readPrivateChunk(pngFile, chunkType, true);
        return objects.isEmpty() ? null : objects.get(objects.size() - 1);
    }

    /**
     * 读取PNG文件中指定类型的私有块
     *
     * @param pngFile   PNG文件对象
     * @param chunkType 要读取的chunk类型
     */
    public static <T> List<T> readAllPrivateChunks(File pngFile, String chunkType) throws IOException, ClassNotFoundException {
        return readPrivateChunk(pngFile, chunkType, false);
    }

    /**
     * 从PNG输入流中读取第一个指定类型的私有chunk
     *
     * @param inputStream 要读取的PNG输入流
     * @param chunkType   要读取的chunk类型
     */
    public static <T> T readFirstPrivateChunk(InputStream inputStream, String chunkType) throws IOException, ClassNotFoundException {
        List<T> objects = readPrivateChunk(inputStream, chunkType, true);
        return objects.isEmpty() ? null : objects.get(0);
    }

    /**
     * 从PNG输入流中读取最后一个指定类型的私有chunk
     *
     * @param inputStream 要读取的PNG输入流
     * @param chunkType   要读取的chunk类型
     */
    public static <T> T readLastPrivateChunk(InputStream inputStream, String chunkType) throws IOException, ClassNotFoundException {
        List<T> objects = readPrivateChunk(inputStream, chunkType, true);
        return objects.isEmpty() ? null : objects.get(objects.size() - 1);
    }

    /**
     * 读取PNG输入流中指定类型的所有私有块
     *
     * @param inputStream 要读取的PNG输入流
     * @param chunkType   要读取的chunk类型
     */
    public static <T> List<T> readAllPrivateChunks(InputStream inputStream, String chunkType) throws IOException, ClassNotFoundException {
        return readPrivateChunk(inputStream, chunkType, false);
    }

    /**
     * 写入压缩的文本块数据
     *
     * @param dos     数据输出流
     * @param keyWord zTxt数据目标关键字
     * @param text    zTxt数据目标文本内容
     */
    private static void writeZTxtChunk(DataOutputStream dos, String keyWord, String text) throws IOException {
        // 创建字节数组输出流，用于存储zTXt块的数据
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // 创建数据输出流，用于向zTXt块中写入数据
        DataOutputStream chunkData = new DataOutputStream(baos);

        // 写入关键字，并以0字节结束
        chunkData.writeBytes(keyWord);
        chunkData.writeByte(0);

        // 写入一个0字节，作为保留字段，留待将来使用
        chunkData.writeByte(0);

        // 创建一个压缩器实例，用于压缩文本数据
        Deflater deflater = new Deflater();
        // 创建字节数组输出流，用于存储压缩后的数据
        ByteArrayOutputStream compressedData = new ByteArrayOutputStream();
        // 使用压缩器创建一个压缩输出流，用于压缩文本数据
        try (DeflaterOutputStream dosCompress = new DeflaterOutputStream(compressedData, deflater)) {
            // 将文本数据转换为字节数组，并写入压缩输出流进行压缩
            dosCompress.write(text.getBytes(StandardCharsets.UTF_8));
        }
        // 将压缩后的数据写入zTXt块的数据流中
        chunkData.write(compressedData.toByteArray());

        // 获取zTXt块的字节数组表示
        byte[] chunkContent = baos.toByteArray();
        // 写入zTXt块到数据输出流中，包括块类型、块数据和CRC校验码
        writeChunk(dos, "zTXt", chunkContent, calculateCRC("zTXt".getBytes(), chunkContent));
    }

    /**
     * 读取空字节终止的字符串
     * 在字节数组中读取直到遇到空字节(0)为止的字节序列，并将其转换为字符串
     */
    private static String readNullTerminatedString(byte[] data) {
        int i = 0;
        // 循环直到遇到空字节(0)或字节数组的末尾
        while (i < data.length && data[i] != 0) {
            i++;
        }
        // 将从字节数组的起始位置到找到的空字节(不包括空字节)之间的字节转换为字符串返回
        return new String(data, 0, i);
    }

    /**
     * 对压缩后的文本进行解压缩
     */
    private static String inflateText(byte[] compressedText) throws IOException {
        // 创建一个字节输入流，用于读取压缩后的文本数据
        ByteArrayInputStream bais = new ByteArrayInputStream(compressedText);
        // 创建一个InflaterInputStream，用于解压缩字节流
        InflaterInputStream inflater = new InflaterInputStream(bais);
        // 创建一个字节输出流，用于存储解压缩后的数据
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // 创建一个缓冲区，用于临时存储解压缩的数据
        byte[] buffer = new byte[1024];
        // 读取的字节数
        int len;
        // 循环读取并解压缩数据，直到输入流结束
        while ((len = inflater.read(buffer)) != -1) {
            // 将解压缩的数据写入输出流
            baos.write(buffer, 0, len);
        }
        // 关闭解压缩流，释放资源
        inflater.close();
        // 将解压缩后的字节流转换为字符串，并使用UTF-8编码
        return baos.toString(StandardCharsets.UTF_8.toString());
    }

    /**
     * 验证给定的字节数组是否为有效的PNG文件头
     */
    private static boolean isPNGHeaderValid(byte[] header) {
        // 定义PNG文件格式的签名，这是一个长度为8的字节数组
        byte[] pngSignature = new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        // 遍历header和pngSignature，逐字节比较
        for (int i = 0; i < pngSignature.length; i++) {
            if (header[i] != pngSignature[i]) return false;
        }
        return true;
    }

    /**
     * 从PNG文件中读取特定类型的私有块数据
     */
    private static <T> List<T> readPrivateChunk(File pngFile, String chunkType, boolean readFirst) throws IOException, ClassNotFoundException {
        List<T> result = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(pngFile);
             DataInputStream dis = new DataInputStream(fis)) {

            // 跳过PNG文件的头部
            byte[] pngHeader = new byte[8];
            dis.readFully(pngHeader);

            // 循环读取PNG文件中的所有私有块
            while (dis.available() > 0) {
                int length = dis.readInt();
                byte[] typeBuffer = new byte[4];
                dis.readFully(typeBuffer);

                String currentChunkType = new String(typeBuffer, StandardCharsets.UTF_8);

                byte[] data = new byte[length];
                dis.readFully(data);

                // 跳过校验和部分，因为当前方法不处理校验和
                dis.skipBytes(4);

                // 当前私有块类型与目标类型匹配时，将数据反序列化并添加到结果列表中
                if (currentChunkType.equals(chunkType)) {
                    result.add(deserializeObject(data));
                    // 如果只需要读取第一个匹配的私有块，则立即返回结果
                    if (readFirst) {
                        return result;
                    }
                }
            }
        }
        return result;
    }

    /**
     * 读取PNG输入流中的指定类型的私有块
     */
    private static <T> List<T> readPrivateChunk(InputStream inputStream, String chunkType, boolean stopAtFirst) throws IOException, ClassNotFoundException {
        List<T> privateChunks = new ArrayList<>();

        try (DataInputStream dis = new DataInputStream(inputStream)) {
            byte[] pngHeader = new byte[8];
            dis.readFully(pngHeader);
            if (!isPNGHeaderValid(pngHeader)) {
                throw new IOException("Invalid PNG file.");
            }
            while (dis.available() > 0) {
                int length = dis.readInt();
                byte[] chunkTypeBytes = new byte[4];
                dis.readFully(chunkTypeBytes);
                String chunkName = new String(chunkTypeBytes);
                byte[] data = new byte[length];
                dis.readFully(data);
                // 跳过CRC
                dis.skipBytes(4);
                // 如果块类型匹配
                if (chunkName.equals(chunkType)) {
                    T chunkObject = deserializeObject(data);
                    privateChunks.add(chunkObject);
                    if (stopAtFirst) {
                        break;
                    }
                }
            }
        }

        return privateChunks;
    }

    /**
     * 写入数据块到输出流中
     */
    private static void writeChunk(DataOutputStream dos, String chunkType, byte[] data, int crc) throws IOException {
        // 写入数据长度，以便接收方知道预期接收的数据量
        dos.writeInt(data.length);
        // 写入数据块类型，以便接收方可以根据类型处理数据
        dos.writeBytes(chunkType);
        // 写入实际数据
        dos.write(data);
        // 写入CRC校验码，以便接收方可以校验数据的完整性
        dos.writeInt(crc);
    }

    /**
     * 从输入流中读取一个整数
     */
    private static int readInt(InputStream is) throws IOException {
        return (is.read() << 24) | (is.read() << 16) | (is.read() << 8) | is.read();
    }

    /**
     * 计算输入字节数组的CRC校验码
     */
    private static int calculateCRC(byte[] type, byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(type);
        crc32.update(data);
        return (int) crc32.getValue();
    }

    /**
     * 序列化对象为字节数组
     */
    private static byte[] serializeObject(Object obj) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            // 将对象序列化到流中
            oos.writeObject(obj);
            // 返回流中的字节数组表示
            return baos.toByteArray();
        }
    }

    /**
     * 反序列化字节数组为对象
     */
    @SuppressWarnings("unchecked")
    private static <T> T deserializeObject(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (T) ois.readObject();
        }
    }


    public static BufferedImage readImage(File file) throws IOException {
        return ImageIO.read(file);
    }

    public static void writeImage(BufferedImage image, File file) throws IOException {
        ImageIO.write(image, "png", file);
    }

    public static Color getPixelColor(BufferedImage image, int x, int y) {
        int rgb = image.getRGB(x, y);
        return new Color(rgb, true);
    }

    public static BufferedImage cropImage(BufferedImage src, int x, int y, int width, int height) {
        return src.getSubimage(x, y, width, height);
    }

    public static BufferedImage concatImagesHorizontally(BufferedImage img1, BufferedImage img2) {
        int width = img1.getWidth() + img2.getWidth();
        int height = Math.max(img1.getHeight(), img2.getHeight());

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics g = result.getGraphics();
        g.drawImage(img1, 0, 0, null);
        g.drawImage(img2, img1.getWidth(), 0, null);
        g.dispose();
        return result;
    }

    public static BufferedImage concatImagesVertically(BufferedImage img1, BufferedImage img2) {
        int width = Math.max(img1.getWidth(), img2.getWidth());
        int height = img1.getHeight() + img2.getHeight();

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics g = result.getGraphics();
        g.drawImage(img1, 0, 0, null);
        g.drawImage(img2, 0, img1.getHeight(), null);
        g.dispose();
        return result;
    }

    public static BufferedImage createBlankImage(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }
}
