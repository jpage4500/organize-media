package com.jpage4500.organize.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class FileUtils {
    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);

    // when max filesize is exceeded - remove all but this percent of records (should be between 50-90%)
    private static final double MAX_EXCEEDED_KEEP_PERCENT = .70;

    /**
     * remove the first 30% of the given file's lines (assuming file is made up of multiple lines)
     * - see MAX_EXCEEDED_KEEP_PERCENT
     */
    public static void truncateFile(File file) {
        log.debug("truncateFile: filesize too large: {} bytes, {}", file.length(), file.getAbsolutePath());
        // file is getting too large!!
        int numLines;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            for (numLines = 0; (reader.readLine()) != null; numLines++) {
                // just counting lines first..
            }
            reader.close();

            int discardLines = numLines - ((int) (numLines * MAX_EXCEEDED_KEEP_PERCENT));

            // create backup file
            File backupFile = new File(file.getParentFile(), file.getName() + ".bak");
            FileOutputStream fos = new FileOutputStream(backupFile, false);
            OutputStreamWriter writer = new OutputStreamWriter(fos);

            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String line;
            for (int count = 0; (line = reader.readLine()) != null; count++) {
                // skip the first < discardLines lines
                if (count < discardLines) {
                    continue;
                }
                writer.append(line);
                writer.append('\n');
            }
            writer.close();
            fos.close();
            reader.close();

            // finally - move backup file
            boolean isDelete = file.delete();
            boolean isRename = backupFile.renameTo(file);
            log.debug("truncateFile: max filesize exceeded! {}, lines:{}, removing:{}, delete:{}, rename:{}", file.length(), numLines, discardLines, isDelete, isRename);
        } catch (Exception e) {
            log.error("truncateFile: Exception: file:{}, {}", file, e.getMessage());
        }
    }

    public static File replaceExt(File file, String ext) {
        String subPath = file.getAbsolutePath();
        int lastPos = subPath.lastIndexOf('.');
        if (lastPos < 0) return null;
        if (!ext.startsWith(".")) ext = "." + ext;
        subPath = subPath.substring(0, lastPos) + ext;
        return new File(subPath);
    }
}
