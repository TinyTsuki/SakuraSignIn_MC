package xin.vanilla.sakura.util;

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

public class PNGUtils {

    /**
     * 根据关键字读取PNG文件中的zTxt信息
     *
     * @param pngFile       PNG文件，方法操作的主体
     * @param targetKeyword 目标关键字，用于在zTxt数据中查找
     * @return 返回与目标关键字对应的字符串，如果找不到则返回null
     * @throws IOException 如果无法读取文件或数据，抛出IO异常
     */
    public static String readZTxtByKey(File pngFile, String targetKeyword) throws IOException {
        // 首先读取PNG文件中的所有zTxt信息到Map中
        Map<String, String> ztxtMap = readAllZTxt(pngFile);
        // 从Map中根据关键字获取对应的值，如果没有找到则返回null
        return ztxtMap.getOrDefault(targetKeyword, null);
    }

    /**
     * 读取PNG文件中的所有zTXt（压缩文本）块，并将其解压缩为键值对映射
     * zTXt块包含一个关键字和一个压缩的文本字符串，该方法将关键字与解压缩后的文本字符串一起返回
     *
     * @param pngFile PNG文件对象，用于读取其中的zTXt块
     * @return 包含解压缩后文本字符串的映射，以关键字为键
     * @throws IOException 如果PNG文件无效或遇到IO错误时抛出
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
                // 跳过校验和部分，因为我们只关心文本数据
                dis.skipBytes(4);
                // 如果块的名称是"zTXt"
                if (chunkName.equals("zTXt")) {
                    // 读取空字符终止的字符串，这个字符串是关键字
                    String keyword = readNullTerminatedString(data);
                    // 计算下一个字段的索引位置
                    int index = keyword.length() + 1;

                    // 读取压缩方法的字段
                    int compressionMethod = data[index];
                    // 将索引指向下一个字段
                    index += 1;
                    // 如果压缩方法不是0（表示未压缩），抛出异常
                    if (compressionMethod != 0) {
                        throw new IOException("Unsupported compression method in zTXt block.");
                    }
                    // 创建一个字节数组，用于存储压缩的文本
                    byte[] compressedText = new byte[length - index];
                    // 将数据中的压缩文本复制到字节数组中
                    System.arraycopy(data, index, compressedText, 0, compressedText.length);
                    // 解压缩文本
                    String decompressedText = inflateText(compressedText);
                    // 将关键字和解压缩的文本存入映射中
                    ztxtMap.put(keyword, decompressedText);
                }
            }
        }
        return ztxtMap;
    }

    /**
     * 根据关键字从输入流中读取PNG的zTXt信息
     *
     * @param inputStream   PNG输入流，方法操作的主体
     * @param targetKeyword 目标关键字，用于在zTXt数据中查找
     * @return 返回与目标关键字对应的字符串，如果找不到则返回null
     * @throws IOException 如果无法读取数据，抛出IO异常
     */
    public static String readZTxtByKey(InputStream inputStream, String targetKeyword) throws IOException {
        // 首先读取PNG文件中的所有zTxt信息到Map中
        Map<String, String> ztxtMap = readAllZTxt(inputStream);
        // 从Map中根据关键字获取对应的值，如果没有找到则返回null
        return ztxtMap.getOrDefault(targetKeyword, null);
    }

    /**
     * 读取输入流中的所有zTXt（压缩文本）块，并将其解压缩为键值对映射
     * zTXt块包含一个关键字和一个压缩的文本字符串，该方法将关键字与解压缩后的文本字符串一起返回
     *
     * @param inputStream PNG输入流对象，用于读取其中的zTXt块
     * @return 包含解压缩后文本字符串的映射，以关键字为键
     * @throws IOException 如果PNG流无效或遇到IO错误时抛出
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
                // 跳过校验和部分，因为我们只关心文本数据
                dis.skipBytes(4);
                // 如果块的名称是"zTXt"
                if (chunkName.equals("zTXt")) {
                    // 读取空字符终止的字符串，这个字符串是关键字
                    String keyword = readNullTerminatedString(data);
                    // 计算下一个字段的索引位置
                    int index = keyword.length() + 1;

                    // 读取压缩方法的字段
                    int compressionMethod = data[index];
                    // 将索引指向下一个字段
                    index += 1;
                    // 如果压缩方法不是0（表示未压缩），抛出异常
                    if (compressionMethod != 0) {
                        throw new IOException("Unsupported compression method in zTXt block.");
                    }
                    // 创建一个字节数组，用于存储压缩的文本
                    byte[] compressedText = new byte[length - index];
                    // 将数据中的压缩文本复制到字节数组中
                    System.arraycopy(data, index, compressedText, 0, compressedText.length);
                    // 解压缩文本
                    String decompressedText = inflateText(compressedText);
                    // 将关键字和解压缩的文本存入映射中
                    ztxtMap.put(keyword, decompressedText);
                }
            }
        }
        return ztxtMap;
    }


    /**
     * 根据关键字更新zTxt标签信息，并写入到新的文件中
     * <p>
     * 此方法首先读取给定PNG文件中的所有zTxt标签信息，然后根据提供的关键字更新标签信息，
     * 最后将更新后的标签信息连同原始文件内容一起写入到指定的输出文件中
     *
     * @param pngFile    输入的PNG文件，其zTxt标签信息将被读取并更新
     * @param outputFile 输出文件，将包含更新后的zTxt标签信息
     * @param keyword    要更新的zTxt标签的关键字
     * @param text       要更新的zTxt标签的文本内容
     * @throws IOException 如果在读写文件或更新标签信息过程中发生I/O错误
     */
    public static void writeZTxtByKey(File pngFile, File outputFile, String keyword, String text) throws IOException {
        // 读取PNG文件中的所有zTxt标签信息
        Map<String, String> ztxtMap = readAllZTxt(pngFile);
        // 根据提供的关键字更新zTxt标签信息
        ztxtMap.put(keyword, text);
        // 将更新后的标签信息写入到新的文件中
        writeZTxt(pngFile, outputFile, ztxtMap);
    }

    /**
     * 向PNG文件中添加zTXT chunk，并将结果保存到输出文件中
     *
     * @param pngFile    输入的PNG文件
     * @param outputFile 输出文件，可以是新的PNG文件
     * @param zTxtData   包含zTXT chunk数据的Map，键是关键字，值是文本数据
     * @throws IOException 如果在文件操作或数据写入过程中发生I/O错误
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
     * <p>
     * 此方法用于在PNG文件中添加一个私有的、自定义类型的块该方法重载了写入私有块的操作，
     * 允许用户指定PNG文件的位置、输出文件的位置、块的类型以及要写入的对象数据
     *
     * @param pngFile    输入的PNG文件，表示要读取其内容以定位私有块写入位置的文件
     * @param outputFile 输出文件，表示写入新内容后保存的文件
     * @param chunkType  块类型，表示要写入的私有块的类型，通常是一个字符串标识
     * @param object     要写入的对象，表示任何类型的对象数据，将被写入PNG文件的私有块中
     * @throws IOException 如果在读写过程中发生I/O错误，将抛出此异常
     */
    public static void writePrivateChunk(File pngFile, File outputFile, String chunkType, Object object) throws IOException {
        // 调用重载方法，实现写入私有块的逻辑，最后一个参数为false表示不启用压缩
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
     * @throws IOException 如果读写过程中发生I/O错误
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
     * @return 如果存在，则返回第一个私有chunk的对象；否则返回null
     * @throws IOException            如果读取文件时发生错误
     * @throws ClassNotFoundException 如果chunk中包含的类不存在
     */
    public static <T> T readFirstPrivateChunk(File pngFile, String chunkType) throws IOException, ClassNotFoundException {
        // 读取指定类型的第一个私有chunk，如果存在则返回chunk对象，否则返回null
        List<T> objects = readPrivateChunk(pngFile, chunkType, true);
        return objects.isEmpty() ? null : objects.get(0);
    }

    /**
     * 读取PNG文件中指定类型的最后一个私有块数据
     * <p>
     * 此方法专注于提取并返回PNG文件中指定类型的最后一个私有块数据它首先通过调用
     * readPrivateChunk方法读取所有该类型的私有块数据，然后返回列表中的最后一个元素如果列表为空，
     * 则返回null这种方法对于需要处理PNG文件中私有块数据的场景非常有用
     *
     * @param pngFile   PNG文件对象，用于读取私有块数据
     * @param chunkType 指定要读取的私有块类型
     * @return 返回指定类型私有块中的最后一个数据项，如果不存在则返回null
     * @throws IOException            如果读取PNG文件过程中发生输入输出异常
     * @throws ClassNotFoundException 如果在私有块数据中使用的类未找到
     */
    public static <T> T readLastPrivateChunk(File pngFile, String chunkType) throws IOException, ClassNotFoundException {
        // 读取PNG文件中指定类型的私有块数据，返回包含所有符合条件的私有块数据列表
        List<T> objects = readPrivateChunk(pngFile, chunkType, true);
        // 返回列表中的最后一个元素，如果列表为空，则返回null
        return objects.isEmpty() ? null : objects.get(objects.size() - 1);
    }

    /**
     * 读取PNG文件中指定类型的私有块
     *
     * @param pngFile   PNG文件对象
     * @param chunkType 需要读取的块类型
     * @return 包含指定类型私有块数据的列表
     * @throws IOException            如果读取文件时发生错误
     * @throws ClassNotFoundException 如果私有块类型无法识别
     */
    public static <T> List<T> readAllPrivateChunks(File pngFile, String chunkType) throws IOException, ClassNotFoundException {
        // 调用readPrivateChunk方法，读取PNG文件中指定类型的私有块，不进行压缩
        return readPrivateChunk(pngFile, chunkType, false);
    }

    /**
     * 从PNG输入流中读取第一个指定类型的私有chunk
     *
     * @param inputStream 要读取的PNG输入流
     * @param chunkType   要读取的chunk类型
     * @return 如果存在，则返回第一个私有chunk的对象；否则返回null
     * @throws IOException            如果读取输入流时发生错误
     * @throws ClassNotFoundException 如果chunk中包含的类不存在
     */
    public static <T> T readFirstPrivateChunk(InputStream inputStream, String chunkType) throws IOException, ClassNotFoundException {
        // 读取指定类型的第一个私有chunk，如果存在则返回chunk对象，否则返回null
        List<T> objects = readPrivateChunk(inputStream, chunkType, true);
        return objects.isEmpty() ? null : objects.get(0);
    }

    /**
     * 读取PNG输入流中指定类型的最后一个私有块数据
     *
     * @param inputStream PNG输入流对象，用于读取私有块数据
     * @param chunkType   指定要读取的私有块类型
     * @return 返回指定类型私有块中的最后一个数据项，如果不存在则返回null
     * @throws IOException            如果读取PNG文件过程中发生输入输出异常
     * @throws ClassNotFoundException 如果在私有块数据中使用的类未找到
     */
    public static <T> T readLastPrivateChunk(InputStream inputStream, String chunkType) throws IOException, ClassNotFoundException {
        // 读取PNG输入流中指定类型的私有块数据，返回包含所有符合条件的私有块数据列表
        List<T> objects = readPrivateChunk(inputStream, chunkType, true);
        // 返回列表中的最后一个元素，如果列表为空，则返回null
        return objects.isEmpty() ? null : objects.get(objects.size() - 1);
    }

    /**
     * 读取PNG输入流中指定类型的所有私有块
     *
     * @param inputStream PNG输入流对象
     * @param chunkType   需要读取的块类型
     * @return 包含指定类型私有块数据的列表
     * @throws IOException            如果读取输入流时发生错误
     * @throws ClassNotFoundException 如果私有块类型无法识别
     */
    public static <T> List<T> readAllPrivateChunks(InputStream inputStream, String chunkType) throws IOException, ClassNotFoundException {
        // 调用readPrivateChunk方法，读取PNG输入流中指定类型的私有块，不进行压缩
        return readPrivateChunk(inputStream, chunkType, false);
    }

    /**
     * 写入压缩的文本块数据
     * 该方法用于压缩给定的文本数据，并将其以zTXt块的形式写入到指定的数据输出流中
     * zTXt块是一种用于存储压缩文本数据的PNG块格式
     *
     * @param dos     数据输出流，用于将zTXt块数据写入到PNG文件中
     * @param keyword 关键字，用于标识zTXt块中的文本内容
     * @param text    要压缩并写入的文本数据
     * @throws IOException 如果在写入过程中发生I/O错误
     */
    private static void writeZTxtChunk(DataOutputStream dos, String keyword, String text) throws IOException {
        // 创建字节数组输出流，用于存储zTXt块的数据
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // 创建数据输出流，用于向zTXt块中写入数据
        DataOutputStream chunkData = new DataOutputStream(baos);

        // 写入关键字，并以0字节结束
        chunkData.writeBytes(keyword);
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
     *
     * @param data 字节数组，包含以空字节终止的字符串数据
     * @return 返回读取到的字符串如果在字节数组中找不到空字节，则返回整个字节数组构成的字符串
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
     * <p>
     * 该方法使用Java的压缩和解压缩API，对传入的压缩文本字节数组进行解压缩，并返回解压缩后的字符串
     * 它首先创建一个字节输入流，然后使用InflaterInputStream进行解压缩，最后将解压缩后的字节流转换为字符串
     *
     * @param compressedText 压缩后的文本字节数组
     * @return 解压缩后的字符串
     * @throws IOException 如果在解压缩过程中发生I/O错误，如读取压缩数据失败或内存不足等
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
     * PNG文件头是一个特定的字节序列，用于标识一个文件是否符合PNG（便携式网络图形）格式
     * 本方法通过比较字节数组的前8个字节与PNG文件头的签名来判断是否有效
     *
     * @param header 要验证的字节数组，通常是一个图像文件的开头部分
     * @return 如果字节数组与PNG文件头签名完全匹配，则返回true，表示这是一个有效的PNG文件头；否则返回false
     */
    private static boolean isPNGHeaderValid(byte[] header) {
        // 定义PNG文件格式的签名，这是一个长度为8的字节数组
        byte[] pngSignature = new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        // 遍历header和pngSignature，逐字节比较
        for (int i = 0; i < pngSignature.length; i++) {
            // 如果任何一个字节不匹配，则返回false，表示这不是一个有效的PNG文件头
            if (header[i] != pngSignature[i]) return false;
        }
        // 所有字节都匹配，返回true，表示这是一个有效的PNG文件头
        return true;
    }

    /**
     * 从PNG文件中读取特定类型的私有块数据
     * <p>
     * 此方法旨在解析PNG文件中特定类型的私有块数据，并将其反序列化为指定类型T的列表
     * 它可以选择性地只读取第一个符合类型的私有块，或者继续读取所有符合类型的私有块
     *
     * @param pngFile   PNG文件对象，指向包含私有块的文件
     * @param chunkType 字符串，指定感兴趣的私有块类型
     * @param readFirst 布尔值，指示是否仅读取第一个符合类型的私有块
     * @return List<T> 包含反序列化对象的列表
     * @throws IOException            当读取文件发生错误时抛出
     * @throws ClassNotFoundException 当反序列化过程中无法找到类时抛出
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
        // 返回包含所有匹配的反序列化对象的列表
        return result;
    }

    /**
     * 读取PNG输入流中的指定类型的私有块
     *
     * @param inputStream PNG输入流对象
     * @param chunkType   需要读取的块类型
     * @param stopAtFirst 如果为true，则读取第一个匹配的块后停止；否则读取所有匹配的块
     * @return 包含指定类型私有块数据的列表
     * @throws IOException            如果读取输入流时发生错误
     * @throws ClassNotFoundException 如果私有块类型无法识别
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
     * 此方法用于将一个数据块按照特定的格式写入到输出流中，数据块包括数据长度、类型、实际数据和CRC校验码
     *
     * @param dos       数据输出流，用于写入数据块
     * @param chunkType 数据块的类型，以字符串形式表示
     * @param data      实际要写入的数据块内容，以字节数组形式提供
     * @param crc       数据块的CRC校验码，用于数据完整性校验
     * @throws IOException 如果在写入过程中发生I/O错误，将抛出此异常
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
     * 此方法通过读取输入流中的四个字节，将其组合成一个整数返回
     * 之所以需要这样读取，是因为某些协议或文件格式可能以这种方式存储整数，
     * 比如BigEndian格式，其中每个字节代表整数的不同部分
     *
     * @param is 输入流，可以是文件输入流、网络输入流等任何实现了InputStream接口的流
     * @return 由输入流中读取的四个字节组成的整数
     * @throws IOException 如果在读取过程中发生I/O错误，比如流提前结束等
     */
    private static int readInt(InputStream is) throws IOException {
        // 读取第一个字节并左移24位，放在整数的高8位
        // 读取第二个字节并左移16位，放在整数的次高8位
        // 读取第三个字节并左移8位，放在整数的次低8位
        // 读取第四个字节，放在整数的低8位
        // 使用位或操作将四个字节合并成一个整数
        return (is.read() << 24) | (is.read() << 16) | (is.read() << 8) | is.read();
    }

    /**
     * 计算输入字节数组的CRC校验码
     * 该方法使用CRC32算法，首先更新校验对象的值，然后返回计算得到的CRC校验码
     *
     * @param type 类型字节数组，用于CRC计算
     * @param data 数据字节数组，用于CRC计算
     * @return 返回计算得到的CRC32校验码
     */
    private static int calculateCRC(byte[] type, byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(type);
        crc32.update(data);
        return (int) crc32.getValue();
    }

    /**
     * 序列化对象为字节数组
     *
     * @param obj 要序列化的对象
     * @return 返回序列化后的字节数组
     * @throws IOException 如果序列化过程中发生I/O错误
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
     * 泛型方法：反序列化字节数组为对象
     * 该方法用于将一个字节数组（通常是由序列化操作生成的）反序列化回原来对应的对象类型
     * 使用了泛型<T>以支持任何对象类型
     *
     * @param data 字节数组，包含了被序列化对象的数据
     * @return T 类型的对象，即反序列化后的原始对象类型
     * @throws IOException            如果在读取或写入流的过程中发生I/O错误
     * @throws ClassNotFoundException 如果对象的类在反序列化时未找到
     */
    @SuppressWarnings("unchecked") // 抑制未检查的类型转换警告，因为泛型擦除性质
    private static <T> T deserializeObject(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data); // 创建字节数组输入流
             ObjectInputStream ois = new ObjectInputStream(bais)) { // 创建对象输入流
            return (T) ois.readObject(); // 从流中读取对象，并进行不安全的类型转换
        }
    }
}
