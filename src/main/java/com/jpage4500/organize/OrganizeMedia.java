package com.jpage4500.organize;

import com.jpage4500.organize.utils.FileUtils;
import com.jpage4500.organize.utils.TextUtils;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class OrganizeMedia {
    // min video size = 50 Meg
    private static final int MIN_VIDEO_LENGTH = 50 * 1000000;

    private static Pattern TV_PATTERN = Pattern.compile("[sS][0-9][0-9][eE][0-9][0-9]");
    private static Pattern YEAR_PATTERN = Pattern.compile("\\(?[12][90][0-9][0-9]\\)?");

    private static boolean isTestMode;
    private static File tvFolder;
    private static File movieFolder;

    public static void main(String[] args) {
        if (args.length < 3) {
            printUsage();
            System.exit(0);
        }

        tvFolder = new File(args[0]);
        movieFolder = new File(args[1]);
        File file = new File(args[2]);

        if (args.length > 3) {
            try {
                isTestMode = TextUtils.equalsIgnoreCaseAny(args[3], "test");
                if (isTestMode) {
                    System.out.println("** TEST MODE **");
                }
            } catch (Exception e) {
            }
        }

        System.out.println("running with: TV:" + tvFolder + ", MOVIE:" + movieFolder + ", ARG:" + file);
        if (!tvFolder.isDirectory()) {
            System.out.println("invalid TV folder: " + tvFolder);
            return;
        } else if (!movieFolder.isDirectory()) {
            System.out.println("invalid MOVIE folder: " + movieFolder);
            return;
        } else if (!file.exists()) {
            System.out.println("invalid FILE: " + file);
            return;
        }
        processFile(file);
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
    // Tom.Clancys.Jack.Ryan.S03.COMPLETE.REPACK.720p.AMZN.WEBRip.x264-GalaxyTV[TGx]
    //    Tom.Clancys.Jack.Ryan.S03E01.720p.AMZN.WEBRip.x264-GalaxyTV.mkv (379.6 MB)
    // Glass.Onion.A.Knives.Out.Mystery.2022.720p.NF.WEBRip.900MB.x264-GalaxyRG[TGx]
    //     Glass.Onion.A.Knives.Out.Mystery.2022.720p.NF.WEBRip.900MB.x264-GalaxyRG.mkv (903.4 MB)
    // Avatar The Way of Water 2022 HDTS 1080p x264 AAC - QRips
    //     Avatar The Way of Water 2022 HDTS 1080p x264 AAC - QRips.mkv (1.9 GB)
    // Strange.World.2022.1080p.10bit.WEBRip.6CH.x265.HEVC-PSA.mkv (1.6 GB)
    // 1923.S01E02.720p.WEB.x265-MiNX[TGx]
    //     1923.S01E02.720p.WEB.x265-MiNX.mkv (215.1 MB)
    // Tulsa.King.S01E07.1080p.WEB.h264-TRUFFLE[TGx]
    //     Tulsa.King.S01E07.1080p.WEB.h264-TRUFFLE.mkv (1.6 GB)
    // Real.Time.with.Bill.Maher.S20E35.1080p.HEVC.x265-MeGusta[eztv.re].mkv
    // Last.Week.Tonight.with.John.Oliver.S09E30.XviD-AFG[eztv.re].avi
    // Avatar.The.Way.Of.Water.2022.UltraHD.HEVC.Dual.YG.mkv (13.2 GB)
    // Avatar - The Way of Water (2022) [Hindi HQ Audio CAM RIP].mkv (5.4 GB)
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
            fileInfo.ext = name.substring(extPos);
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
            Matcher matcher = TV_PATTERN.matcher(part);
            if (matcher.matches()) {
                // TV show
                fileInfo.type = MediaType.TYPE_TV;
                fileInfo.tvVersion = part;
                try {
                    String seasonStr = part.substring(1, 3);
                    fileInfo.season = Integer.parseInt(seasonStr);
                } catch (Exception e) {
                    System.out.println("Exception: " + part);
                }
                break;
            }

            // look for year (not in first part)
            if (i > 0) {
                matcher = YEAR_PATTERN.matcher(part);
                if (matcher.matches()) {
                    // got year
                    if (!TextUtils.startsWith(part, "(")) {
                        part = "(" + part + ")";
                        sb.append(" (" + part + ")");
                        break;
                    } else {
                        // year is already in parens
                        sb.append(' ');
                        sb.append(part);
                        break;
                    }
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

    private static void moveMedia(FileInfo fileInfo) {
        File dest;
        if (fileInfo.type == MediaType.TYPE_TV) {
            dest = new File(tvFolder, fileInfo.name);
        } else {
            dest = new File(movieFolder, fileInfo.name);
        }
        // create TV/Movie folder
        if (!isTestMode) {
            dest.mkdir();
        }

        // create full filename
        if (fileInfo.type == MediaType.TYPE_TV) {
            // "<show>/<show> S01E01.ext"
            dest = new File(dest, fileInfo.name + " " + fileInfo.tvVersion + fileInfo.ext);
        } else {
            // "<movie>/<movie> (YEAR).ext"
            dest = new File(dest, fileInfo.name + fileInfo.ext);
        }

        // move file to folder
        System.out.println("Moving: " + fileInfo.file + " to: " + dest);
        if (!isTestMode) {
            fileInfo.file.renameTo(dest);
        }

        // check for subtitle with matching name
        File subFile = FileUtils.replaceExt(fileInfo.file, ".srt");
        if (subFile != null && subFile.exists()) {
            // move to same folder
            File destSubFile = FileUtils.replaceExt(dest, ".srt");
            System.out.println("Moving: " + subFile + " to: " + destSubFile);
            if (!isTestMode && destSubFile != null) {
                subFile.renameTo(destSubFile);
            }
        }
    }

    private static boolean isVideo(String name) {
        String[] VIDEO_EXT = new String[]{
            ".mp4", ".avi", ".mkv", ".mov", ".wmv"
        };
        return TextUtils.endsWithIgnoreCase(name, VIDEO_EXT);
    }

    enum MediaType {
        TYPE_UNKNOWN,
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
            type = MediaType.TYPE_UNKNOWN;
        }
    }

    private static void printUsage() {
        System.out.println("OrganizeMedia: Version: " + Build.versionName + ", Built: " + Build.buildDate);
        System.out.println("Usage:");
        System.out.println("java OrganizeMedia <TV FOLDER> <MOVIE FOLDER> <media folder or file>");
    }
}
