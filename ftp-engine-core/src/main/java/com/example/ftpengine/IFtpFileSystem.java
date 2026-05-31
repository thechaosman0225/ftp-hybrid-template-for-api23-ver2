package com.example.ftpengine;

import java.io.IOException;

/**
 * Interface defining minimal filesystem operations for the FTP engine.
 * Allows switching between local storage and SAF (Android) storage.
 */
public interface IFtpFileSystem {

    /**
     * Check if a file or directory exists.
     *
     * @param path path relative to the FTP root
     * @return true if exists
     */
    boolean exists(String path);

    /**
     * Check if a path is a directory.
     *
     * @param path path relative to the FTP root
     * @return true if the path exists and is a directory
     */
    boolean isDirectory(String path);

    /**
     * Delete a file or directory.
     *
     * @param path path relative to the FTP root
     * @return true if deletion succeeded
     * @throws IOException on error
     */
    boolean delete(String path) throws IOException;

    /**
     * Create a directory (and any parent directories if needed).
     *
     * @param path path relative to the FTP root
     * @return true if directory created
     * @throws IOException on error
     */
    boolean mkdir(String path) throws IOException;

    /**
     * Rename or move a file or directory.
     *
     * @param from old path
     * @param to   new path
     * @return true if rename succeeded
     * @throws IOException on error
     */
    boolean rename(String from, String to) throws IOException;

    /**
     * List files in a directory.
     *
     * @param path path relative to the FTP root
     * @return array of file/directory names
     * @throws IOException on error
     */
    String[] list(String path) throws IOException;

    /**
     * Read file contents.
     *
     * @param path file path relative to the FTP root
     * @return byte array of file contents
     * @throws IOException on error
     */
    byte[] readFile(String path) throws IOException;

    /**
     * Write data to a file, creating parent directories if needed.
     *
     * @param path file path relative to the FTP root
     * @param data byte array to write
     * @throws IOException on error
     */
    void writeFile(String path, byte[] data) throws IOException;
}
