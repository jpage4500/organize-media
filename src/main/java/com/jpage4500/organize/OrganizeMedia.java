package com.jpage4500.organize;

import com.jpage4500.organize.logging.AppLoggerFactory;
import com.jpage4500.organize.logging.Log;
import com.jpage4500.organize.utils.FileUtils;
import com.jpage4500.organize.utils.GsonHelper;
import com.jpage4500.organize.utils.TextUtils;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class OrganizeMedia {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrganizeMedia.class);

    // min video size = 50 Meg
    private static final int MIN_VIDEO_LENGTH = 50 * 1000000;

    private static final Pattern TV_PATTERN = Pattern.compile("[sS][0-9][0-9][eE][0-9][0-9]");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\(?[12][90][0-9][0-9]\\)?");

    private static final String[] VIDEO_EXT = new String[]{
        ".mp4", ".avi", ".mkv", ".mov", ".wmv"
    };

    private static boolean isTestMode;
    private static File tvFolder;
    private static File movieFolder;
    private static File rootFile;
    private static File runScript;

    /**
     * configuring SLF4J custom logger implementation
     * - call soon after Application:onCreate(); can be called again if debug mode changes
     */
    private static void setupLogging() {
        AppLoggerFactory logger = (AppLoggerFactory) LoggerFactory.getILoggerFactory();
        // tag prefix allows for easy filtering: ie: 'adb logcat | grep PM_'
        //logger.setTagPrefix("DM");
        // set log level that application should log at (and higher)
        logger.setDebugLevel(Log.VERBOSE);
        logger.setMainThreadId(Thread.currentThread().getId());
        //logger.setLogToFile(true);
        //logger.setFileLogLevel(Log.DEBUG);
    }

    public static void main(String[] args) {
        setupLogging();

        if (args.length < 3) {
            printUsage();
            System.exit(0);
        }

        tvFolder = new File(args[0]);
        movieFolder = new File(args[1]);
        rootFile = new File(args[2]);

        if (args.length > 3) {
            String arg4 = args[3];
            if (TextUtils.equalsIgnoreCaseAny(arg4, "test")) {
                isTestMode = true;
                System.out.println("** TEST MODE **");
            } else {
                File arg4File = new File(arg4);
                if (arg4File.exists()) {
                    runScript = arg4File;
                }
            }
        }

        System.out.println("running with: TV:" + tvFolder + ", MOVIE:" + movieFolder + ", ARG:" + rootFile);
        if (!tvFolder.isDirectory()) {
            System.out.println("invalid TV folder: " + tvFolder);
            return;
        } else if (!movieFolder.isDirectory()) {
            System.out.println("invalid MOVIE folder: " + movieFolder);
            return;
        } else if (!rootFile.exists()) {
            System.out.println("invalid FILE: " + rootFile);
            return;
        }
        processFile(rootFile);
    }

    private static void processFile(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                processFile(child);
            }
        } else {
            parseMedia(file);
        }
    }

    // Examples:
    // -- TV --
    // Tv.Show.Name.S03E01.720p.AMZN.WEBRip.x264-GalaxyTV.mkv (379.6 MB)
    // TV Show.S01E02.720p.WEB.x265-MiNX.mkv (215.1 MB)
    // Tv.Show.S01E07.1080p.WEB.h264-TRUFFLE.mkv (1.6 GB)
    // Tv.Show.S20E35.1080p.HEVC.x265-MeGusta[eztv.re].mkv
    // Tv.Show.S09E30.XviD-AFG[eztv.re].avi
    // Tv.Show.2018.S03E01.720p.NF.WEBRip.x264-GalaxyTV[TGx]
    // -- MOVIE --
    // Movie.Name.2022.720p.NF.WEBRip.900MB.x264-GalaxyRG.mkv (903.4 MB)
    // Movie Name 2022 HDTS 1080p x264 AAC - QRips.mkv (1.9 GB)
    // Movie.Name.2022.UltraHD.HEVC.Dual.YG.mkv (13.2 GB)
    // Movie - Name (2022) [Hindi HQ Audio CAM RIP].mkv (5.4 GB)
    private static FileInfo parseMedia(File file) {
        String name = file.getName();
        if (!isVideo(name)) {
            //System.out.println("not a video");
            return null;
        }
        long length = file.length();
        if (length < MIN_VIDEO_LENGTH) {
            //System.out.println("too short");
            return null;
        }
        // ** VIDEO **
        FileInfo fileInfo = new FileInfo(file);
        fileInfo.type = MediaType.TYPE_MOVIE;

        int extPos = name.lastIndexOf('.');
        if (extPos > 0) {
            fileInfo.ext = name.substring(extPos).toLowerCase(Locale.US);
        }

        // -- get display name --
        // replace "." with " "
        name = name.replace('.', ' ');
        String[] nameArr = name.split(" ");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < nameArr.length; i++) {
            String part = nameArr[i];
            //System.out.println("PART: " + part);
            if (TextUtils.equalsIgnoreCaseAny(part, "1080p", "720p", "HDTS", "WEB", "webrip", "HEVC", "UltraHD")) {
                break;
            }

            // look for season/episode
            if (isPatternTv(part, fileInfo)) {
                break;
            }

            // look for year
            // ignore the first part to handle movies like "2001"
            if (i > 0) {
                Matcher matcher = YEAR_PATTERN.matcher(part);
                if (matcher.matches()) {
                    // got year
                    if (!TextUtils.startsWith(part, "(")) {
                        // add year in parens "(year)"
                        part = "(" + part + ")";
                    }
                    sb.append(' ');
                    sb.append(part);

                    // this could be a movie or TV show - look at the next part to try and figure out which
                    if (i + 1 < nameArr.length) {
                        String nextPart = nameArr[i + 1];
                        isPatternTv(nextPart, fileInfo);
                    }
                    break;
                }
            }

            // add part to display name
            if (sb.length() > 0) sb.append(' ');
            sb.append(part);
        }
        String displayName = sb.toString();
        if (TextUtils.isEmpty(displayName)) return null;

        // change to sentence case
        fileInfo.name = TextUtils.toSentenceCase(displayName);

        //System.out.println("GOT: " + fileInfo.name + ", type: " + fileInfo.type + ", file: " + file);
        moveMedia(fileInfo);

        return fileInfo;
    }

    private static boolean isPatternTv(String part, FileInfo fileInfo) {
        Matcher matcher = TV_PATTERN.matcher(part);
        if (matcher.matches()) {
            part = part.toUpperCase(Locale.US);
            // TV show
            fileInfo.type = MediaType.TYPE_TV;
            fileInfo.tvVersion = part;
            try {
                String seasonStr = part.substring(1, 3);
                fileInfo.season = Integer.parseInt(seasonStr);
            } catch (Exception e) {
                System.out.println("isPatternTv: Exception: " + part + ", " + e.getMessage());
            }
            return true;
        }
        return false;
    }

    private static File getDestFolder(FileInfo fileInfo) {
        if (fileInfo.type == MediaType.TYPE_TV) {
            return new File(tvFolder, fileInfo.name);
        } else {
            return new File(movieFolder, fileInfo.name);
        }
    }

    private static void moveMedia(FileInfo fileInfo) {
        File dest = getDestFolder(fileInfo);
        // create TV/Movie folder
        if (!isTestMode) {
            dest.mkdir();
        }

        // create full filename
        File destFile;
        if (fileInfo.type == MediaType.TYPE_TV) {
            // "<show>/<show> S01E01.ext"
            destFile = new File(dest, fileInfo.name + " " + fileInfo.tvVersion + fileInfo.ext);
        } else {
            // "<movie>/<movie> (YEAR).ext"
            destFile = new File(dest, fileInfo.name + fileInfo.ext);
        }

        System.out.println("Moving: " + fileInfo.file + " to: " + destFile);
        if (!isTestMode) {
            // check if video already exists at destination
            if (isVideoExist(destFile)) {
                System.out.println("ALREADY EXISTS: " + destFile + " - FILE: " + fileInfo.file);
                return;
            }
            // move file to folder
            boolean isOk = fileInfo.file.renameTo(destFile);
            if (isOk) {
                runScript(destFile);
            } else {
                System.out.println("ERROR: moving file: " + fileInfo.file + ", to: " + destFile);
            }
        }

        // check for subtitle with matching name
        File subFile = FileUtils.replaceExt(fileInfo.file, ".srt");
        if (subFile != null && subFile.exists()) {
            // move to same folder
            File destSubFile = FileUtils.replaceExt(destFile, ".srt");
            System.out.println("Moving SUBTITLE: " + subFile + " to: " + destSubFile);
            if (!isTestMode && destSubFile != null) {
                subFile.renameTo(destSubFile);
            }
        }
    }

    private static boolean isVideoExist(File file) {
        // check if video file exists as-is
        if (file.exists()) return true;
        // also check if video file exists but with a different extension (.avi, .mkv, etc)
        String path = file.getAbsolutePath().toLowerCase(Locale.US);
        // remove extension from video
        int pos = path.lastIndexOf('.');
        if (pos < 0) return false;
        path = path.substring(0, pos);
        String origExt = path.substring(pos);
        for (String ext : VIDEO_EXT) {
            // ignore same extension as video
            if (TextUtils.equals(origExt, ext)) continue;
            File f = new File(path + ext);
            if (f.exists()) {
                System.out.println("isVideoExist: ALTERNATE found: " + f.getAbsolutePath() + ", " + file.getAbsolutePath());
                return true;
            }
        }
        return false;
    }

    private static boolean isVideo(String name) {
        return TextUtils.endsWithIgnoreCase(name, VIDEO_EXT);
    }

    private static void runScript(File dest) {
        if (runScript == null) return;

        ProcessBuilder processBuilder = new ProcessBuilder();
        List<String> commandList = new ArrayList<>();
        commandList.add(runScript.getAbsolutePath());
        commandList.add(dest.getAbsolutePath());
        processBuilder.command(commandList);
        try {
            System.out.println("running: " + runScript + ", param: " + dest);
            Process process = processBuilder.start();
            process.waitFor();

            List<String> resultList = readInputStream(process.getInputStream());
            if (!resultList.isEmpty()) {
                log.trace("runScript: RESULTS: {}", GsonHelper.toJson(resultList));
            }
            List<String> errorList = readInputStream(process.getErrorStream());
            if (!errorList.isEmpty()) {
                log.error("runScript: ERROR: {}", GsonHelper.toJson(errorList));
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        }
    }

    enum MediaType {
        TYPE_MOVIE,
        TYPE_TV
    }

    static class FileInfo {
        File file;
        String name;
        String ext;
        MediaType type;
        int season;
        int episode;
        String tvVersion;

        public FileInfo(File file) {
            this.file = file;
        }
    }

    private static List<String> readInputStream(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        List<String> resultList = new ArrayList<>();
        try {
            while (true) {
                line = reader.readLine();
                if (line == null) break;
                else if (line.isEmpty()) continue;
                //log.debug("runScript: {}", line);
                resultList.add(line);
            }
            reader.close();
        } catch (IOException e) {
            log.error("readInputStream: Exception: {}", e.getMessage());
        }
        return resultList;
    }

    private static void printUsage() {
        System.out.println("OrganizeMedia: Version: " + Build.versionName + ", Built: " + Build.buildDate);
        System.out.println("Usage:");
        System.out.println("java OrganizeMedia <TV FOLDER> <MOVIE FOLDER> <MEDIA (folder or file)> <SCRIPT>");
        System.out.println("  - <TV FOLDER>: folder to move TV shows to");
        System.out.println("  - <MOVIE FOLDER>: folder to move movies to");
        System.out.println("  - <MEDIA>: folder or file to process");
        System.out.println("  - <SCRIPT>: *optional script to run when complete. Will be run with destination tv/movie folder");
    }
}
