package io.github.paulem.fjc.utils;

import io.github.paulem.fjc.FlowJsonCreator;
import io.github.paulem.fjc.flow.UrlMod;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class FileUtils {
    public static Path getActualJar() {
        try {
            return Path.of(FlowJsonCreator.class.getProtectionDomain().getCodeSource().getLocation()
                    .toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Read the file and calculate the SHA-1 checksum
     *
     * @param file
     *            the file to read
     * @return the hex representation of the SHA-1 using uppercase chars
     * @throws FileNotFoundException
     *             if the file does not exist, is a directory rather than a
     *             regular file, or for some other reason cannot be opened for
     *             reading
     * @throws IOException
     *             if an I/O error occurs
     * @throws NoSuchAlgorithmException
     *             should never happen
     */
    public static String calcSHA1(File file) throws FileNotFoundException,
            IOException, NoSuchAlgorithmException {

        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        try (InputStream input = new FileInputStream(file)) {

            byte[] buffer = new byte[8192];
            int len = input.read(buffer);

            while (len != -1) {
                sha1.update(buffer, 0, len);
                len = input.read(buffer);
            }

            return new HexBinaryAdapter().marshal(sha1.digest());
        }
    }

    public static void downloadFile(String url, File file) {
        try (BufferedInputStream in = new BufferedInputStream(new URL(url).openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static UrlMod getDownloadedMod(String jarUrl, String fileName) throws IOException, NoSuchAlgorithmException {
        File file = getActualJar().getParent().resolve(UUID.randomUUID() + ".jar").toFile();

        downloadFile(jarUrl, file);

        String sha1 = calcSHA1(file);
        long size = file.length();

        file.delete();

        return new UrlMod(fileName, jarUrl, sha1, size);
    }
}
