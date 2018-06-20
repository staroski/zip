package br.com.staroski.zip;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Utility class to compress and extract ZIP files.
 * 
 * @author Ricardo Artur Staroski
 */
public final class ZipUtils {

    /**
     * Commpresses the file or directory into the specified ZIP file.
     * 
     * @param input
     *            The input file or folder.
     * @param output
     *            The ZIP output file.
     *
     * @return The decompression checksum.
     */
    public static long compress(final File input, final File output) throws IOException {
        if (!input.exists()) {
            throw new IOException(input.getName() + " does not exists!");
        }
        if (output.exists()) {
            if (output.isDirectory()) {
                throw new IllegalArgumentException("\"" + output.getAbsolutePath() + "\" is not a file!");
            }
        } else {
            final File parent = output.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            output.createNewFile();
        }
        Checksum checksum = createChecksum();
        final ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output));
        zip.setLevel(Deflater.BEST_COMPRESSION);
        compressInternal(null, input, zip, checksum);
        zip.flush();
        zip.finish();
        zip.close();
        return checksum.getValue();
    }

    /**
     * Extracts a ZIP file to the specified folder.
     * 
     * @param input
     *            The input ZIP file.
     * @param output
     *            The output folder.
     * @return The decompression checksum.
     */
    public static long extract(final File input, final File output) throws IOException {
        if (input.exists()) {
            if (input.isDirectory()) {
                throw new IllegalArgumentException("\"" + input.getAbsolutePath() + "\" is not a file!");
            }
        } else {
            throw new IllegalArgumentException("\"" + input.getAbsolutePath() + "\" does not exists!");
        }
        if (output.exists()) {
            if (output.isFile()) {
                throw new IllegalArgumentException("\"" + output.getAbsolutePath() + "\" is not a directory!");
            }
        }
        Checksum checksum = createChecksum();
        final ZipInputStream zip = new ZipInputStream(new FileInputStream(input));
        extractInternal(zip, output, checksum);
        zip.close();
        return checksum.getValue();
    }

    // adds a file to the ZIP
    private static void compressInternal(final String path, final File file, final ZipOutputStream zip, final Checksum checksum) throws IOException {
        final boolean dir = file.isDirectory();
        String name = file.getName();
        name = (path != null ? path + "/" + name : name);
        final ZipEntry item = new ZipEntry(name + (dir ? "/" : ""));
        item.setTime(file.lastModified());
        zip.putNextEntry(item);
        if (dir) {
            zip.closeEntry();
            final File[] arquivos = file.listFiles();
            for (int i = 0; i < arquivos.length; i++) {
                // use recursion to add another file to the ZIP
                compressInternal(name, arquivos[i], zip, checksum);
            }
        } else {
            item.setSize(file.length());
            final FileInputStream input = new FileInputStream(file);
            copy(input, zip, checksum);
            input.close();
            zip.closeEntry();
        }
    }

    /**
     * Copy the content from the InputStream to the OutputStream .
     * 
     * @param from
     *            The input stream.
     * @param to
     *            The output stream.
     * @param checksum
     *            The file's checksum.
     * @throws IOException
     */
    private static void copy(InputStream from, OutputStream to, Checksum checksum) throws IOException {
        byte[] bytes = new byte[8192];
        for (int read = -1; (read = from.read(bytes)) != -1; to.write(bytes, 0, read), checksum.update(bytes, 0, read)) {}
        to.flush();
    }

    private static Checksum createChecksum() {
        return new CRC32();
    }

    // Removes the specified file from the ZIP
    private static void extractInternal(final ZipInputStream zip, final File dir, Checksum checksum) throws IOException {
        ZipEntry entry = null;
        while ((entry = zip.getNextEntry()) != null) {
            String name = entry.getName();
            name = name.replace('/', File.separatorChar);
            name = name.replace('\\', File.separatorChar);
            File file = new File(dir, name);
            if (entry.isDirectory()) {
                file.mkdirs();
            } else {
                boolean exists = file.exists();
                if (!exists) {
                    final File parent = file.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    file.createNewFile();
                }
                // apply a special handling for existent hidden and read-olnly files
                boolean isHidden = false;
                boolean isReadOnly = false;
                if (exists) {
                    isHidden = file.isHidden();
                    if (isHidden) {
                        Files.setAttribute(file.toPath(), "dos:hidden", false);
                    }
                    isReadOnly = !file.canWrite();
                    if (isReadOnly) {
                        file.setWritable(true);
                    }
                }

                OutputStream output = new FileOutputStream(file);
                copy(zip, output, checksum);
                output.close();

                // undo the special handling for existent hidden and read-olnly files
                if (exists) {
                    if (isReadOnly) {
                        file.setWritable(false);
                    }
                    if (isHidden) {
                        Files.setAttribute(file.toPath(), "dos:hidden", true);
                    }
                }
            }
            file.setLastModified(entry.getTime());
        }
    }

    // Private constructor - it makes no sense to instantiate this class
    private ZipUtils() {}
}