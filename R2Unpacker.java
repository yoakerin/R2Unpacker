package com.yoake.r2pak;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

public class R2Unpacker {

    private static final int[] XOR_DATA = {
        0xFF21, 0x834F, 0x675F, 0x0034, 0xF237, 0x815F, 0x4765, 0x0233
    };

    public static void main(String[] args) throws Exception {
        File pakFile = new File("/Users/yoake/Documents/r2beat/rnr_script.pak");
        String outputBase = "/Users/yoake/Documents/r2beat/";
        
        byte[] buffer = Files.readAllBytes(pakFile.toPath());
        ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

     
        int fileIndexOffset = bb.getInt(buffer.length - 9);
        int fileCount = bb.getInt(buffer.length - 5);
        
        System.out.println("文件总数: " + fileCount + ", 索引起始位置: 0x" + Integer.toHexString(fileIndexOffset));

        int currentOffset = fileIndexOffset;

        for (int i = 0; i < fileCount; i++) {
            int fileNameLength = buffer[currentOffset] & 0xFF;
            int fileType = buffer[currentOffset + 1] & 0xFF;
            
            int dataOffset = bb.getInt(currentOffset + 2);
            int encodedSize = bb.getInt(currentOffset + 6);
            int originalSize = bb.getInt(currentOffset + 10);
            
            int fileNameStart = currentOffset + 14;
     
            String fileName = new String(buffer, fileNameStart, fileNameLength, "GBK");
            
            System.out.printf("[%d] 类型: %d | 路径: %s | 大小: %d -> %d\n", 
                               i, fileType, fileName, encodedSize, originalSize);

    
            byte[] encodedData = Arrays.copyOfRange(buffer, dataOffset, dataOffset + encodedSize);
            byte[] decodedData;

            if (fileType == 3) { // LZSSXOR
                decodedData = decodeLzssXor(encodedData, originalSize);
            } else if (fileType == 1) { // LZSS
                decodedData = decodeLzss(encodedData, originalSize);
            } else { // RAW or DIRECTORY
                decodedData = encodedData;
            }

      
            if (fileType != 2) { // 过滤掉目录类型
                File outFile = new File(outputBase + fileName);
                outFile.getParentFile().mkdirs();
                Files.write(outFile.toPath(), decodedData);
            }
            currentOffset = fileNameStart + fileNameLength + 1;
        }
        System.out.println("解压完成！");
    }

    // 核心算法：LZSS + XOR
    public static byte[] decodeLzssXor(byte[] buffer, int originalSize) {
        byte[] out = new byte[originalSize];
        int inIdx = 0, outIdx = 0;
        int flags = 0, counter = 0;

        while (inIdx < buffer.length && outIdx < originalSize) {
            if ((counter++ & 7) != 0) {
                flags >>= 1;
            } else {
                flags = (buffer[inIdx++] & 0xFF) ^ 0xb4;
            }

            if ((flags & 1) != 0) {
                // 读取 16 位 LE 并异或
                int rawPair = (buffer[inIdx] & 0xFF) | ((buffer[inIdx + 1] & 0xFF) << 8);
                int pair = rawPair ^ XOR_DATA[(flags >> 3) & 7];
                
                int pos = pair & 0x0FFF;
                int length = (pair >> 12) + 2;
                
                // 字典复制
                for (int i = 0; i < length && outIdx < originalSize; i++) {
                    out[outIdx] = out[outIdx - pos];
                    outIdx++;
                }
                inIdx += 2;
            } else {
                if (inIdx < buffer.length) {
                    out[outIdx++] = (byte) ((buffer[inIdx++] & 0xFF) ^ 0xb4);
                }
            }
        }
        return out;
    }

    // 标准 LZSS
    public static byte[] decodeLzss(byte[] buffer, int originalSize) {
        byte[] out = new byte[originalSize];
        int inIdx = 0, outIdx = 0;
        int flags = 0;
        while (inIdx < buffer.length && outIdx < originalSize) {
            if (((flags >>= 1) & 0x100) == 0) {
                flags = (buffer[inIdx++] & 0xFF) | 0xff00;
            }
            if ((flags & 1) != 0) {
                int pair = (buffer[inIdx] & 0xFF) | ((buffer[inIdx + 1] & 0xFF) << 8);
                int pos = pair & 0x0FFF;
                int length = (pair >> 12) + 2;
                for (int i = 0; i < length && outIdx < originalSize; i++) {
                    out[outIdx] = out[outIdx - pos];
                    outIdx++;
                }
                inIdx += 2;
            } else {
                out[outIdx++] = buffer[inIdx++];
            }
        }
        return out;
    }
}