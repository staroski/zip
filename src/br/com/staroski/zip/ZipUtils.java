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
 * Utility class to compress or extract ZIP files.<br>
 * Use {@link ZipUtils#compress(File, File)} method to compress a file or directory.<br>
 * Use {@link ZipUtils#extract(File, File)} method to extract the contents of a compressed file.
 * 
 * @author Ricardo Artur Staroski
 */
public final class ZipUtils {

    /**
     * Commpresses the file or directory into the specified ZIP file.
     * 
     * @param input
     *            The input file or folder.
     * 
     * @param output
     *            The ZIP output file.
     *
     * @return The decompression checksum.
     * 
     * @throws IOException
     *             If some I/O exception occurs.
     * 
     * @see ZipUtils#extract(File, File)
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
        final Checksum checksum = checksum();
        final ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output));
        zip.setLevel(Deflater.BEST_COMPRESSION);
        doCompress(null, input, zip, checksum);
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
     * 
     * @param output
     *            The output folder.
     * 
     * @return The decompression checksum.
     * 
     * @throws IOException
     *             If some I/O exception occurs.
     * 
     * @see ZipUtils#compress(File, File)
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
        final Checksum checksum = checksum();
        final ZipInputStream zip = new ZipInputStream(new FileInputStream(input));
        doExtract(zip, output, checksum);
        zip.close();
        return checksum.getValue();
    }

    // factory method used internally to create the checksum object during compression or extraction.
    private static Checksum checksum() {
        return new CRC32();
    }

    // used internally to copy the content from the InputStream to the OutputStream
    private static void copy(InputStream from, OutputStream to, Checksum checksum) throws IOException {
        byte[] bytes = new byte[8192]; // 8 KB buffer
        for (int read = -1; (read = from.read(bytes)) != -1; to.write(bytes, 0, read), checksum.update(bytes, 0, read)) {}
        to.flush();
    }

    // used internally to add a file to the ZIP during compression
    private static void doCompress(final String path, final File file, final ZipOutputStream zip, final Checksum checksum) throws IOException {
        final boolean dir = file.isDirectory();
        String name = file.getName();
        name = (path == null ? name : path + "/" + name);
        final ZipEntry item = new ZipEntry(name + (dir ? "/" : ""));
        item.setTime(file.lastModified());
        zip.putNextEntry(item);
        if (dir) {
            zip.closeEntry();
            for (File child : file.listFiles()) {
                // use recursion to add another file to the ZIP
                doCompress(name, child, zip, checksum);
            }
        } else {
            item.setSize(file.length());
            final FileInputStream input = new FileInputStream(file);
            copy(input, zip, checksum);
            input.close();
            zip.closeEntry();
        }
    }

    // used internally to extract the specified file from the ZIP
    private static void doExtract(final ZipInputStream zip, final File dir, Checksum checksum) throws IOException {
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
                // do the special handling for existent hidden and read-olnly files
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
                    if (isHidden) {
                        Files.setAttribute(file.toPath(), "dos:hidden", true);
                    }
                    if (isReadOnly) {
                        file.setWritable(false);
                    }
                }
            }
            file.setLastModified(entry.getTime());
        }
    }

    // Private constructor - it makes no sense to instantiate this class
    private ZipUtils() {}
}