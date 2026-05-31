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
 * KEY SAF RULE: DocumentsContract.buildChildDocumentsUriUsingTree() always
 * requires the TREE Uri as its first argument, not a document Uri.
 * The original code passed `current` (which becomes a document Uri after the
 * first path segment is resolved), causing all Cursor queries to return null
 * for any path deeper than one level, and making list() throw IOException
 * "Directory not found" — ultimately surfacing as "550 LIST failed" in FileZilla.
 *
 * Fixes applied:
 *   BUG 1 — resolveDocumentUri(): always pass rootUri (tree) to
 *            buildChildDocumentsUriUsingTree(), never `current`.
 *   BUG 2 — list(): same tree/document confusion fixed.
 *   BUG 3 — list() now fetches MIME_TYPE in the same Cursor query so
 *            callers can check isDirectory without a second SAF round-trip
 *            per file (which was also broken by Bug 1).
 *
 * License: Apache 2.0
 */
public class SAFFileObject {

    private final Context context;
    private final ContentResolver resolver;
    private final Uri rootUri;        // Always the tree Uri — never changes.
    private final String path;        // FTP-style absolute path, e.g. "/folder/file.txt"
    private String mimeType;          // Populated by list(); null when constructed directly.

    public SAFFileObject(Context ctx, Uri rootUri, String path) {
        this.context  = ctx;
        this.resolver = ctx.getContentResolver();
        this.rootUri  = rootUri;
        this.path     = normalizePath(path);
    }

    /** Package-private constructor used by list() to carry mime type without extra queries. */
    SAFFileObject(Context ctx, Uri rootUri, String path, String mimeType) {
        this(ctx, rootUri, path);
        this.mimeType = mimeType;
    }

    private String normalizePath(String p) {
        if (p == null || p.isEmpty()) return "/";
        if (!p.startsWith("/")) p = "/" + p;
        return p;
    }

    public String getPath() { return path; }

    public boolean exists() {
        return (resolveDocumentUri() != null);
    }

    public boolean isDirectory() {
        // Fast path: mime already fetched by list().
        if (mimeType != null) {
            return DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
        }
        Uri doc = resolveDocumentUri();
        if (doc == null) return false;

        try (Cursor c = resolver.query(doc,
                new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String mime = c.getString(0);
                return DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
            }
        } catch (Exception ignored) {}
        return false;
    }

    public boolean isFile() {
        return exists() && !isDirectory();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CORE: resolve FTP path → SAF document Uri
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Walks the FTP path segment-by-segment through SAF, always using rootUri
     * as the tree Uri in buildChildDocumentsUriUsingTree().
     *
     * Returns a document Uri on success, null if any segment is not found.
     */
    Uri resolveDocumentUri() {
        try {
            String clean = path.replaceAll("/+", "/");

            // Root maps directly to the tree root document.
            if (clean.equals("/")) return rootUri;

            // Start from the root document id.
            String currentDocId = DocumentsContract.getDocumentId(rootUri);

            String[] parts = clean.split("/");
            Uri result = null;

            for (String part : parts) {
                if (part == null || part.isEmpty()) continue;

                // BUG 1 FIX: always use rootUri (tree) as the first argument.
                Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                        rootUri,        // ← tree Uri, constant throughout
                        currentDocId    // ← current folder's document id
                );

                try (Cursor c = resolver.query(
                        childrenUri,
                        new String[]{
                                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME
                        },
                        null, null, null)) {

                    if (c == null) return null;

                    String foundDocId = null;
                    while (c.moveToNext()) {
                        String docId = c.getString(0);
                        String name  = c.getString(1);
                        if (part.equals(name)) {
                            foundDocId = docId;
                            break;
                        }
                    }

                    if (foundDocId == null) return null;

                    currentDocId = foundDocId;
                    result = DocumentsContract.buildDocumentUriUsingTree(rootUri, foundDocId);
                }
            }

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIST
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lists direct children of this directory.
     * Each returned SAFFileObject carries its MIME type so callers can call
     * isDirectory() without triggering another SAF query.
     */
    public List<SAFFileObject> list() throws IOException {
        Uri doc = resolveDocumentUri();
        if (doc == null) throw new IOException("Directory not found: " + path);

        // Derive document id from the resolved document Uri.
        String docId = DocumentsContract.getDocumentId(doc);
        if (docId == null) {
            // Fallback for root where getDocumentId on a tree Uri returns the root id.
            docId = DocumentsContract.getTreeDocumentId(rootUri);
        }

        // BUG 2 FIX: use rootUri (tree) as the first argument, not `doc`.
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                rootUri,  // ← tree Uri, always
                docId
        );

        List<SAFFileObject> result = new ArrayList<>();

        // BUG 3 FIX: also fetch MIME_TYPE here so isDirectory() is free.
        try (Cursor c = resolver.query(childrenUri,
                new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE      // ← added
                },
                null, null, null)) {

            if (c != null) {
                while (c.moveToNext()) {
                    // String docIdChild = c.getString(0); // available if needed
                    String name = c.getString(1);
                    String mime = c.getString(2);

                    String childPath = path.equals("/") ? "/" + name : path + "/" + name;

                    result.add(new SAFFileObject(context, rootUri, childPath, mime));
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to list: " + path, e);
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ / WRITE / DELETE / MKDIR / RENAME
    // ─────────────────────────────────────────────────────────────────────────

    public InputStream openInput() throws IOException {
        Uri doc = resolveDocumentUri();
        if (doc == null) throw new IOException("File not found: " + path);
        return resolver.openInputStream(doc);
    }

    public OutputStream openOutput(boolean append) throws IOException {
        Uri doc = resolveDocumentUri();
        if (doc == null) {
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

    public boolean renameTo(String newPath) throws IOException {
        String newName = newPath.substring(newPath.lastIndexOf("/") + 1);
        Uri doc = resolveDocumentUri();
        if (doc == null) throw new IOException("File not found: " + path);
        return DocumentsContract.renameDocument(resolver, doc, newName) != null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private Uri createFile(String fullPath) throws IOException {
        String parentPath = fullPath.substring(0, fullPath.lastIndexOf('/'));
        String filename   = fullPath.substring(fullPath.lastIndexOf('/') + 1);

        Uri parentDoc = new SAFFileObject(context, rootUri,
                parentPath.isEmpty() ? "/" : parentPath).resolveDocumentUri();
        if (parentDoc == null)
            throw new IOException("Parent folder does not exist: " + parentPath);

        Uri newDoc = DocumentsContract.createDocument(
                resolver, parentDoc, "application/octet-stream", filename);
        if (newDoc == null)
            throw new IOException("Failed to create file: " + filename);
        return newDoc;
    }

    private Uri createFolder(String fullPath) throws IOException {
        String parentPath  = fullPath.substring(0, fullPath.lastIndexOf('/'));
        String folderName  = fullPath.substring(fullPath.lastIndexOf('/') + 1);

        Uri parentDoc = new SAFFileObject(context, rootUri,
                parentPath.isEmpty() ? "/" : parentPath).resolveDocumentUri();
        if (parentDoc == null)
            throw new IOException("Parent folder does not exist: " + parentPath);

        Uri newDoc = DocumentsContract.createDocument(
                resolver, parentDoc,
                DocumentsContract.Document.MIME_TYPE_DIR, folderName);
        if (newDoc == null)
            throw new IOException("Failed to create folder: " + folderName);
        return newDoc;
    }
}
