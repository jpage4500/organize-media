package com.jpage4500.organize.utils;

import java.io.File;

public class FileUtils {
    public static File replaceExt(File file, String ext) {
        String subPath = file.getAbsolutePath();
        int lastPos = subPath.lastIndexOf('.');
        if (lastPos < 0) return null;
        if (!ext.startsWith(".")) ext = "." + ext;
        subPath = subPath.substring(0, lastPos) + ext;
        return new File(subPath);
    }
}
