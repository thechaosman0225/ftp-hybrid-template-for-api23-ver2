package com.example.ftpengine.saf;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * SAF-backed representation of a single file or folder.
 *
 * License: Apache 2.0
 */
public class SAFFileObject {

    private final Context context;
    private final ContentResolver resolver;
    private final Uri rootUri;
    private final String path; // "/folder/file.txt"

    public SAFFileObject(Context ctx, Uri rootUri, String path) {
        this.context = ctx;
        this.resolver = ctx.getContentResolver();
        this.rootUri = rootUri;
        this.path = normalizePath(path);
    }

    private String normalizePath(String p) {
        if (p == null || p.isEmpty()) return "/";
        if (!p.startsWith("/")) p = "/" + p;
        return p;
    }

    public String getPath() {
        return path;
    }

    public boolean exists() {
        return (resolveDocumentUri() != null);
    }

    public boolean isDirectory() {
        Uri doc = resolveDocumentUri();
        if (doc == null) return false;

        try (Cursor c = resolver.query(doc,
                new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE},
                null, null, null)) {

            if (c != null && c.moveToFirst()) {
                String mime = c.getString(0);
                return DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
            }
        }
        return false;
    }

    public boolean isFile() {
        Uri doc = resolveDocumentUri();
        if (doc == null) return false;

        try (Cursor c = resolver.query(doc,
                new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE},
                null, null, null)) {

            if (c != null && c.moveToFirst()) {
                String mime = c.getString(0);
                return !DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
            }
        }
        return false;
    }

    /**
     * Convert FTP path (/folder/a.txt) → SAF Uri for file.
     */
    private Uri resolveDocumentUri() {
    try {
        String clean = path.replaceAll("/+", "/");
        if (clean.equals("/")) return rootUri;

        Uri current = rootUri;
        String[] parts = clean.split("/");

        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;

            Uri childrenUri =
                    DocumentsContract.buildChildDocumentsUriUsingTree(
                            current,
                            DocumentsContract.getDocumentId(current)
                    );

            Cursor c = resolver.query(
                    childrenUri,
                    new String[]{
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    },
                    null, null, null
            );

            if (c == null) return null;

            Uri next = null;

            while (c.moveToNext()) {
                String docId = c.getString(0);
                String name = c.getString(1);

                if (part.equals(name)) {
                    next = DocumentsContract.buildDocumentUriUsingTree(
                            rootUri,
                            docId
                    );
                    break;
                }
            }

            c.close();

            if (next == null) return null;

            current = next;
        }

        return current;

    } catch (Exception e) {
        e.printStackTrace();
        return null;
    }
}

    /**
     * Scans children of SAF dir for matching name.
     */
    private Uri findChildUri(Uri parent, String name) {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                parent,
                DocumentsContract.getDocumentId(parent)
        );

        try (Cursor c = resolver.query(
                childrenUri,
                new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                },
                null, null, null)) {

            if (c == null) return null;

            while (c.moveToNext()) {
                String docId = c.getString(0);
                String childName = c.getString(1);

                if (name.equals(childName)) {
                    return DocumentsContract.buildDocumentUriUsingTree(rootUri, docId);
                }
            }
        }
        return null;
    }

    public List<SAFFileObject> list() throws IOException {
        Uri doc = resolveDocumentUri();
        if (doc == null) throw new IOException("Directory not found: " + path);

        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                doc,
                DocumentsContract.getDocumentId(doc)
        );

        List<SAFFileObject> result = new ArrayList<>();

        try (Cursor c = resolver.query(children,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {

            if (c != null) {
                while (c.moveToNext()) {
                    String docId = c.getString(0);
                    String name = c.getString(1);

                    result.add(new SAFFileObject(
                            context,
                            rootUri,
                            path.equals("/") ? "/" + name : path + "/" + name
                    ));
                }
            }
        }

        return result;
    }

    public InputStream openInput() throws IOException {
        Uri doc = resolveDocumentUri();
        if (doc == null) throw new IOException("File not found: " + path);
        return resolver.openInputStream(doc);
    }

    public OutputStream openOutput(boolean append) throws IOException {
        Uri doc = resolveDocumentUri();

        if (doc == null) {
            // Must create the file
            doc = createFile(path);
        }

        String mode = append ? "wa" : "w";
        return resolver.openOutputStream(doc, mode);
    }

    public boolean delete() throws IOException {
        Uri doc = resolveDocumentUri();
        if (doc == null) return false;
        return DocumentsContract.deleteDocument(resolver, doc);
    }

    public boolean mkdir() throws IOException {
        createFolder(path);
        return true;
    }

    /**
     * SAF create file
     */
    private Uri createFile(String fullPath) throws IOException {
        String parentPath = fullPath.substring(0, fullPath.lastIndexOf('/'));
        String filename = fullPath.substring(fullPath.lastIndexOf('/') + 1);

        SAFFileObject parent = new SAFFileObject(context, rootUri, parentPath);

        Uri parentDoc = parent.resolveDocumentUri();
        if (parentDoc == null)
            throw new IOException("Parent folder does not exist: " + parentPath);

        String mime = "application/octet-stream";

        Uri newDoc = DocumentsContract.createDocument(
                resolver,
                parentDoc,
                mime,
                filename
        );

        if (newDoc == null)
            throw new IOException("Failed to create file: " + filename);

        return newDoc;
    }

    /**
     * SAF create folder
     */
    private Uri createFolder(String fullPath) throws IOException {
        String parentPath = fullPath.substring(0, fullPath.lastIndexOf('/'));
        String folderName = fullPath.substring(fullPath.lastIndexOf('/') + 1);

        SAFFileObject parent = new SAFFileObject(context, rootUri, parentPath);

        Uri parentDoc = parent.resolveDocumentUri();
        if (parentDoc == null)
            throw new IOException("Parent folder does not exist: " + parentPath);

        Uri newDoc = DocumentsContract.createDocument(
                resolver,
                parentDoc,
                DocumentsContract.Document.MIME_TYPE_DIR,
                folderName
        );

        if (newDoc == null)
            throw new IOException("Failed to create folder: " + folderName);

        return newDoc;
    }

    public boolean renameFrom(String newName) throws IOException {
        Uri doc = resolveDocumentUri();
        if (doc == null) throw new IOException("File not found: " + path);

        return DocumentsContract.renameDocument(resolver, doc, newName) != null;
    }

    public boolean renameTo(String newPath) throws IOException {
        String newName = newPath.substring(newPath.lastIndexOf("/") + 1);
        return renameFrom(newName);
    }
}
